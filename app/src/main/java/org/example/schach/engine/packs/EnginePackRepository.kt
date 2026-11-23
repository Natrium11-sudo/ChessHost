package org.example.schach.engine.packs

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import org.example.schach.ui.i18n.trNow
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface PackInstallState {
    data object NotInstalled : PackInstallState

    data class Downloading(val doneBytes: Long, val totalBytes: Long) : PackInstallState {
        val progress: Float
            get() = if (totalBytes <= 0) 0f else (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    data class Installed(val directory: String) : PackInstallState

    data class Failed(val message: String) : PackInstallState
}

/** Ein Ordner unter `filesDir/engines/`, der einen `.installed`-Marker besitzt. */
data class InstalledPack(
    val id: String,
    val directory: File,
    val bytes: Long,
    val version: String?,
)

/**
 * Lädt Engine-Pakete in den privaten App-Speicher, prüft Prüfsummen und hält
 * den Installationszustand. Eingebaute Engines brauchen keine Installation.
 *
 * Der Ordner `filesDir/engines/<id>/` ist die einzige Wahrheit: Alles, was dort
 * einen Marker hat, gilt als installiert – auch wenn der Katalog den Eintrag
 * nicht mehr kennt. Solche Waisen bleiben so sichtbar und löschbar.
 */
class EnginePackRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "engines")
    private val prefs = appContext.getSharedPreferences("engine_packs", Context.MODE_PRIVATE)

    private val _states = MutableStateFlow<Map<String, PackInstallState>>(emptyMap())
    val states: StateFlow<Map<String, PackInstallState>> = _states.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledPack>>(emptyList())
    val installed: StateFlow<List<InstalledPack>> = _installed.asStateFlow()

    /** Aktivierte Packs, deren Binary in der APK liegt ([EngineOrigin.OPTIONAL]). */
    private val _activated = MutableStateFlow(readActivated())
    val activated: StateFlow<Set<String>> = _activated.asStateFlow()

    fun isActivated(packId: String): Boolean = packId in _activated.value

    fun setActivated(packId: String, active: Boolean) {
        val next = _activated.value.toMutableSet().apply { if (active) add(packId) else remove(packId) }
        prefs.edit().putStringSet(KEY_ACTIVATED, next).apply()
        _activated.value = next
        setState(
            packId,
            if (active) PackInstallState.Installed("") else PackInstallState.NotInstalled,
        )
    }

    private fun readActivated(): Set<String> =
        prefs.getStringSet(KEY_ACTIVATED, emptySet())?.toSet() ?: emptySet()

    fun directoryOf(packId: String): File = File(root, packId)

    fun installedDirectory(pack: EnginePack): File? {
        if (pack.isBuiltin) return null
        val directory = directoryOf(pack.id)
        return if (File(directory, MARKER).exists()) directory else null
    }

    /** Gleicht den Zustand mit Dateisystem und Aktivierung ab (z. B. nach App-Start). */
    fun sync(packs: List<EnginePack>) {
        val current = _states.value
        _states.value = packs.filterNot { it.isBuiltin }.associate { pack ->
            val directory = installedDirectory(pack)
            val state: PackInstallState = when {
                pack.isOptional -> if (isActivated(pack.id)) {
                    PackInstallState.Installed("")
                } else {
                    PackInstallState.NotInstalled
                }

                directory != null -> PackInstallState.Installed(directory.absolutePath)
                current[pack.id] is PackInstallState.Downloading -> current.getValue(pack.id)
                current[pack.id] is PackInstallState.Failed -> current.getValue(pack.id)
                else -> PackInstallState.NotInstalled
            }
            pack.id to state
        }
        rescanInstalled()
    }

    suspend fun install(pack: EnginePack): Boolean = withContext(Dispatchers.IO) {
        if (pack.isBuiltin) return@withContext true
        if (pack.isOptional) {
            setActivated(pack.id, true)
            return@withContext true
        }
        if (pack.files.isEmpty()) {
            setState(pack.id, PackInstallState.Failed("Für dieses Paket gibt es keine Download-Dateien."))
            return@withContext false
        }

        val coroutineContext = currentCoroutineContext()
        val directory = directoryOf(pack.id)
        val totalBytes = pack.downloadBytes
        var doneBytes = 0L

        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Ordner konnte nicht angelegt werden")
            }
            cleanupPartials(directory)

            for (file in pack.files) {
                coroutineContext.ensureActive()
                val target = File(directory, file.name)

                // Nur übernehmen, was die Prüfsumme auch bestätigt.
                if (target.exists() && matchesHash(target, file.sha256)) {
                    doneBytes += target.length()
                    setState(pack.id, PackInstallState.Downloading(doneBytes, totalBytes))
                    continue
                }
                target.delete()

                val partial = File(directory, file.name + PART)
                partial.delete()
                download(file.url, partial) { read ->
                    if (!coroutineContext.isActive) throw CancellationException("Download abgebrochen")
                    doneBytes += read
                    setState(pack.id, PackInstallState.Downloading(doneBytes, totalBytes))
                }

                val expected = file.sha256
                if (expected != null) {
                    val actual = sha256(partial)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        partial.delete()
                        throw IOException(trNow("Prüfsumme stimmt nicht (%s)", file.name))
                    }
                }

                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException(
                        trNow("Datei konnte nicht gespeichert werden (%s)", file.name),
                    )
                }
            }

            File(directory, MARKER).writeText(
                """{"pack":"${pack.id}","version":"${pack.version}","installedAt":${System.currentTimeMillis()}}""",
            )
            setState(pack.id, PackInstallState.Installed(directory.absolutePath))
            rescanInstalled()
            true
        } catch (cancelled: CancellationException) {
            cleanupPartials(directory)
            setState(pack.id, PackInstallState.NotInstalled)
            rescanInstalled()
            throw cancelled
        } catch (throwable: Throwable) {
            cleanupPartials(directory)
            setState(
                pack.id,
                PackInstallState.Failed(throwable.message ?: "Installation fehlgeschlagen."),
            )
            rescanInstalled()
            false
        }
    }

    /**
     * Entfernt ein Pack: bei [EngineOrigin.OPTIONAL] wird es deaktiviert, bei
     * [EngineOrigin.DOWNLOAD] wird der Ordner gelöscht.
     */
    suspend fun remove(pack: EnginePack): Boolean = withContext(Dispatchers.IO) {
        when {
            pack.isOptional -> {
                setActivated(pack.id, false)
                true
            }

            pack.isBuiltin -> false

            else -> deleteDownload(pack.id)
        }
    }

    /** Entfernt einen Download-Ordner, auch wenn der Katalog ihn nicht mehr führt. */
    suspend fun removeDownload(packId: String): Boolean = withContext(Dispatchers.IO) {
        deleteDownload(packId)
    }

    /** Deaktiviert alle optionalen Packs und löscht alle Downloads samt Waisen. */
    suspend fun removeAll(optionalPackIds: List<String>): Int = withContext(Dispatchers.IO) {
        optionalPackIds.forEach { setActivated(it, false) }
        val ids = _installed.value.map { it.id }.ifEmpty { scanInstalled().map { it.id } }
        ids.forEach { deleteDownload(it) }
        optionalPackIds.size + ids.size
    }

    private fun deleteDownload(packId: String): Boolean {
        val directory = directoryOf(packId)
        val deleted = runCatching { directory.deleteRecursively() }.getOrDefault(false) && !directory.exists()
        setState(packId, PackInstallState.NotInstalled)
        rescanInstalled()
        return deleted
    }

    private fun rescanInstalled() {
        _installed.value = scanInstalled()
    }

    private fun scanInstalled(): List<InstalledPack> {
        val directories = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return directories
            .mapNotNull { directory ->
                val marker = File(directory, MARKER)
                if (!marker.exists()) return@mapNotNull null
                val version = runCatching {
                    JSONObject(marker.readText()).optString("version").takeIf { it.isNotBlank() }
                }.getOrNull()
                InstalledPack(
                    id = directory.name,
                    directory = directory,
                    bytes = directorySize(directory),
                    version = version,
                )
            }
            .sortedBy { it.id }
    }

    private fun directorySize(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun cleanupPartials(directory: File) {
        directory.listFiles()?.filter { it.name.endsWith(PART) }?.forEach { it.delete() }
    }

    private fun matchesHash(file: File, expected: String?): Boolean {
        if (expected == null) return true
        return sha256(file).equals(expected, ignoreCase = true)
    }

    private fun setState(packId: String, state: PackInstallState) {
        _states.value = _states.value.toMutableMap().apply { put(packId, state) }
    }

    private fun download(url: String, target: File, onBytes: (Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SchachAndroid/1.0")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException(
                    trNow("HTTP %s bei %s", code, url.substringAfterLast('/')),
                )
            }
            connection.inputStream.use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MARKER = ".installed"
        private const val PART = ".part"
        private const val KEY_ACTIVATED = "activated_packs"
    }
}
