package org.example.chesshost.engine.packs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.example.chesshost.ui.i18n.trNow
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CatalogStatus(
    val remoteUrl: String? = null,
    val error: String? = null,
    val remoteCount: Int = 0,
    val loadedAt: Long = 0L,
)

/**
 * Der Engine-Katalog: eine JSON-Liste, die mit der App ausgeliefert wird
 * (`assets/engines/catalog.json`) und zur Laufzeit um einen zweiten Katalog aus
 * dem Netz ergänzt oder überschrieben werden kann.
 *
 * Erweitern ohne Codeänderung: entweder die Asset-Datei bearbeiten (neue APK)
 * oder in der App eine Katalog-URL hinterlegen, die dieselbe Struktur liefert.
 * Einträge mit gleicher `id` überschreiben den eingebauten Eintrag, neue `id`s
 * werden angehängt.
 */
class EngineCatalog(private val context: Context) {

    private val bundled: List<EnginePack> = readBundled()
    private var remote: List<EnginePack> = emptyList()

    private val _packs = MutableStateFlow(bundled)
    val packs: StateFlow<List<EnginePack>> = _packs.asStateFlow()

    private val _status = MutableStateFlow(CatalogStatus())
    val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    fun find(id: String?): EnginePack? {
        if (id.isNullOrBlank()) return null
        return _packs.value.firstOrNull { it.id == id }
    }

    /** Lädt den optionalen Netzkatalog. Ohne URL bleibt der eingebaute Katalog aktiv. */
    suspend fun refresh(remoteUrl: String?): CatalogStatus = withContext(Dispatchers.IO) {
        val url = remoteUrl?.trim().orEmpty()
        if (url.isEmpty()) {
            remote = emptyList()
            _packs.value = bundled
            return@withContext CatalogStatus(loadedAt = System.currentTimeMillis()).also { _status.value = it }
        }

        val result = runCatching { fetch(url) }
        result.fold(
            onSuccess = { packs ->
                if (packs.isEmpty()) {
                    remote = emptyList()
                    _packs.value = bundled
                    CatalogStatus(
                        remoteUrl = url,
                        error = "Der Katalog enthält keine Engines.",
                        loadedAt = System.currentTimeMillis(),
                    )
                } else {
                    remote = packs
                    _packs.value = mergeCatalogs(bundled, packs)
                    CatalogStatus(
                        remoteUrl = url,
                        remoteCount = packs.size,
                        loadedAt = System.currentTimeMillis(),
                    )
                }
            },
            onFailure = { throwable ->
                remote = emptyList()
                _packs.value = bundled
                CatalogStatus(
                    remoteUrl = url,
                    error = friendly(throwable.message),
                    loadedAt = System.currentTimeMillis(),
                )
            },
        ).also { _status.value = it }
    }

    private fun friendly(message: String?): String = when {
        message == null -> "Katalog konnte nicht geladen werden."
        message.contains("Cleartext", ignoreCase = true) -> "Nur HTTPS-Adressen werden unterstützt."
        else -> message
    }

    private fun readBundled(): List<EnginePack> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { EnginePack.listFromJson(it.readText()) }
    }.getOrElse { emptyList() }

    private fun fetch(url: String): List<EnginePack> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException(trNow("HTTP %s", code))
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(MAX_CATALOG_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                buffer.copyOf(total)
            }
            val packs = EnginePack.listFromJson(bytes.toString(Charsets.UTF_8))
            if (packs.isEmpty() && bytes.isEmpty()) throw IOException("Leere Antwort")
            return packs
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ASSET = "engines/catalog.json"
        private const val USER_AGENT = "SchachAndroid/1.0"
        private const val MAX_CATALOG_BYTES = 512 * 1024
    }
}
