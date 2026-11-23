package org.example.chesshost.engine

import android.content.Context
import android.os.Build
import org.example.chesshost.engine.packs.EngineBackend
import org.example.chesshost.engine.packs.EngineCatalog
import org.example.chesshost.engine.packs.EngineOrigin
import org.example.chesshost.engine.packs.EnginePack
import org.example.chesshost.engine.packs.EnginePackRepository
import org.example.chesshost.engine.packs.UciConfig
import org.example.chesshost.engine.llm.LLM_PACK_ID
import org.example.chesshost.engine.llm.LlmConfig
import org.example.chesshost.engine.llm.LlmEngineProvider
import org.example.chesshost.engine.llm.llmEnginePack
import java.io.File

/**
 * Ein Ladeweg für eine Engine-Art. Neue Backends brauchen nur eine weitere
 * Implementierung, die hier in [EngineRegistry] registriert wird.
 */
interface EngineProvider {
    val backend: EngineBackend

    fun isAvailable(pack: EnginePack): Boolean

    suspend fun create(pack: EnginePack): ChessEngine?
}

/**
 * Native Engine-Binary aus der APK. Bei Download-Packs kommt zusätzlich ein
 * NNUE-Netz aus dem installierten Ordner dazu; die Binary selbst bleibt in
 * `nativeLibraryDir`, weil Android 10+ nichts anderes ausführen darf.
 */
class BuiltinUciProvider(
    private val context: Context,
    private val repository: EnginePackRepository,
) : EngineProvider {

    override val backend: EngineBackend = EngineBackend.UCI_NATIVE

    override fun isAvailable(pack: EnginePack): Boolean {
        val binary = binaryOf(pack) ?: return false
        if (!binary.exists() || !binary.canExecute()) return false
        // Binary aus der APK: eingebaut oder vom Nutzer aktiviert.
        if (pack.isBundled) return true

        if (repository.installedDirectory(pack) == null) return false
        return netNames(pack).all { name -> File(repository.directoryOf(pack.id), name).exists() }
    }

    override suspend fun create(pack: EnginePack): ChessEngine? {
        val binary = binaryOf(pack) ?: return null
        if (!binary.exists()) return null

        val uci = pack.uci ?: UciConfig()
        val directory = if (pack.isBundled) null else repository.installedDirectory(pack) ?: return null
        val resolved = if (directory == null) {
            uci
        } else {
            uci.copy(
                evalFile = uci.evalFile?.let { File(directory, it).absolutePath },
                evalFileSmall = uci.evalFileSmall?.let { File(directory, it).absolutePath },
            )
        }
        return StockfishEngine(binary, pack.title, resolved)
    }

    private fun binaryOf(pack: EnginePack): File? {
        val name = pack.binary ?: return null
        return File(context.applicationInfo.nativeLibraryDir, name)
    }

    private fun netNames(pack: EnginePack): List<String> =
        listOfNotNull(pack.uci?.evalFile, pack.uci?.evalFileSmall).ifEmpty {
            pack.files.map { it.name }
        }
}

/**
 * Neuronale Engine: Das Netz ist eine reine Datendatei und wird aus dem Store
 * geladen, die Laufzeit (ONNX Runtime) steckt in der APK. Damit ist dieser Weg
 * von Androids Exec-Sperre nicht betroffen – hier wird nichts gestartet.
 */
class OnnxEngineProvider(private val repository: EnginePackRepository) : EngineProvider {

    override val backend: EngineBackend = EngineBackend.ONNX

    override fun isAvailable(pack: EnginePack): Boolean = modelFile(pack) != null

    override suspend fun create(pack: EnginePack): ChessEngine? {
        val model = modelFile(pack) ?: return null
        val elo = pack.elo ?: Maia3Engine.DEFAULT_ELO
        return Maia3Engine(model, pack.title, elo = elo)
    }

    /** Das Netz ist die erste Datei des Packs. */
    private fun modelFile(pack: EnginePack): File? {
        val directory = repository.installedDirectory(pack) ?: return null
        val name = pack.files.firstOrNull()?.name ?: return null
        val file = File(directory, name)
        return file.takeIf { it.exists() && it.length() > 0L }
    }
}

/** Immer vorhandene Notfall-Engine, damit die App nie ohne Gegner dasteht. */
class InternalEngineProvider : EngineProvider {

    override val backend: EngineBackend = EngineBackend.INTERNAL

    override fun isAvailable(pack: EnginePack): Boolean = true

    override suspend fun create(pack: EnginePack): ChessEngine = SimpleEngine()
}

/**
 * Nachinstallierte UCI-Engine. Android verbietet ab API 29 das Ausführen von
 * Dateien aus dem App-Speicher, deshalb ist dieser Weg nur auf älteren Geräten
 * (oder mit Runtime-Paket) nutzbar.
 */
class ExternalUciProvider(private val repository: EnginePackRepository) : EngineProvider {

    override val backend: EngineBackend = EngineBackend.EXTERNAL_UCI

    private val executablesAllowed: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun isAvailable(pack: EnginePack): Boolean =
        executablesAllowed && repository.installedDirectory(pack) != null

    override suspend fun create(pack: EnginePack): ChessEngine? {
        val directory = repository.installedDirectory(pack) ?: return null
        val name = pack.executable ?: return null
        val binary = File(directory, name)
        if (!binary.exists()) return null
        binary.setExecutable(true)
        return StockfishEngine(binary, pack.title, pack.uci ?: UciConfig())
    }
}

/**
 * Zentrale Auflösung von Pack-ID zu Engine-Instanz. Die App kennt nur noch
 * IDs, die Katalog-Einträge entscheiden über den Ladeweg.
 */
class EngineRegistry(
    context: Context,
    private val catalog: EngineCatalog,
    private val repository: EnginePackRepository,
    /** Aktuelle LLM-Konfiguration aus den Einstellungen. */
    private val llmConfig: () -> LlmConfig = { LlmConfig() },
) {

    private val providers: List<EngineProvider> = listOf(
        BuiltinUciProvider(context, repository),
        InternalEngineProvider(),
        ExternalUciProvider(repository),
        OnnxEngineProvider(repository),
        LlmEngineProvider(llmConfig),
    )

    /** Katalog-Einträge plus der synthetische LLM-Gegner aus den Einstellungen. */
    fun packs(): List<EnginePack> =
        catalog.packs.value.filterNot { it.id == LLM_PACK_ID } + llmEnginePack(llmConfig())

    fun find(packId: String?): EnginePack? {
        if (packId == null) return null
        if (packId == LLM_PACK_ID) return llmEnginePack(llmConfig())
        return catalog.find(packId)
    }

    fun providerFor(pack: EnginePack): EngineProvider? =
        providers.firstOrNull { it.backend == pack.backend }

    /** Ab Werk ist keine Engine aktiv: optionale Packs müssen freigeschaltet werden. */
    fun isActive(pack: EnginePack): Boolean = when (pack.origin) {
        EngineOrigin.OPTIONAL -> repository.isActivated(pack.id)
        else -> true
    }

    fun isSupported(pack: EnginePack): Boolean =
        pack.available && isActive(pack) && providerFor(pack)?.isAvailable(pack) == true

    /**
     * Ob der Store dieses Pack hinzufügen kann: optionale Engines werden
     * aktiviert, Netz-Packs heruntergeladen. Neue Download-Backends tragen
     * sich hier ein, sobald sie eine Laufzeitumgebung mitbringen.
     */
    fun canInstall(pack: EnginePack): Boolean = when {
        !pack.available -> false
        pack.isOptional -> providerFor(pack)?.isAvailable(pack) == true
        pack.isBuiltin -> false
        // Netze und Modelle sind Daten: Sie dürfen nachgeladen werden, ohne dass
        // Android etwas ausführen müsste.
        else -> pack.files.isNotEmpty() &&
            pack.backend in DOWNLOADABLE_BACKENDS &&
            providerFor(pack) != null
    }

    /** Startet das Pack einmal und gibt zurück, ob es wirklich läuft. */
    suspend fun probe(pack: EnginePack): Boolean {
        val engine = create(pack) ?: return false
        return try {
            engine.start()
        } finally {
            runCatching { engine.close() }
        }
    }

    fun availablePacks(): List<EnginePack> = packs().filter { isSupported(it) }

    fun defaultPack(): EnginePack? = find(DEFAULT_PACK_ID) ?: availablePacks().firstOrNull()

    fun unavailableReason(pack: EnginePack): String? = when {
        isSupported(pack) -> null
        !pack.available -> pack.note ?: "Noch nicht verfügbar."
        pack.isOptional && !repository.isActivated(pack.id) ->
            "Noch nicht aktiviert – im Store hinzufügen."
        pack.backend == EngineBackend.EXTERNAL_UCI ->
            "Android 10+ erlaubt das Starten nachgeladener Programme nicht. Dafür kommt das Runtime-Paket."
        pack.backend == EngineBackend.ONNX ->
            "Noch nicht heruntergeladen – im Store installieren."
        pack.backend == EngineBackend.LLM ->
            "LLM noch nicht eingerichtet – API-Adresse und Modell in den Einstellungen eintragen."
        pack.backend == EngineBackend.UCI_NATIVE && providerFor(pack)?.isAvailable(pack) != true ->
            if (!pack.isBundled && repository.installedDirectory(pack) == null) {
                "Noch nicht installiert – im Store herunterladen."
            } else {
                "Engine-Datei fehlt in dieser App-Version."
            }
        else -> pack.note ?: "Auf diesem Gerät nicht verfügbar."
    }

    suspend fun create(pack: EnginePack): ChessEngine? {
        if (!isSupported(pack)) return null
        return providerFor(pack)?.create(pack)
    }

    companion object {
        const val DEFAULT_PACK_ID = "stockfish-17.1"

        /** Backends, deren Dateien ohne Ausführungsrecht geladen werden können. */
        private val DOWNLOADABLE_BACKENDS =
            setOf(EngineBackend.UCI_NATIVE, EngineBackend.ONNX)
    }
}
