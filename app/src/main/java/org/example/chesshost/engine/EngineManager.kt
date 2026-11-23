package org.example.chesshost.engine

import org.example.chesshost.engine.packs.EnginePack

/**
 * Verwaltet die aktiven Engine-Instanzen. Für "Engine gegen Engine" werden
 * zwei völlig unabhängige Instanzen erzeugt und parallel betrieben.
 */
class EngineManager(private val registry: EngineRegistry) {

    private val active = mutableListOf<ChessEngine>()

    /** Alle Engines, die auf diesem Gerät gerade spielbar sind. */
    fun engines(): List<EnginePack> = registry.availablePacks()

    /** Alle Katalog-Einträge, auch nicht verfügbare. */
    fun allPacks(): List<EnginePack> = registry.packs()

    fun defaultEngine(): EnginePack? = registry.defaultPack()

    fun pack(packId: String?): EnginePack? = registry.find(packId)

    fun labelOf(packId: String?): String =
        registry.find(packId)?.title ?: defaultEngine()?.title ?: "Engine"

    fun isAvailable(packId: String?): Boolean =
        registry.find(packId)?.let { registry.isSupported(it) } == true

    fun unavailableReason(packId: String?): String? =
        registry.find(packId)?.let { registry.unavailableReason(it) }

    /**
     * Erzeugt eine Engine für die angegebene Pack-ID. Unbekannte oder nicht
     * verfügbare IDs fallen auf die Standard-Engine zurück, danach auf die
     * interne Engine.
     */
    suspend fun createEngine(packId: String? = null): ChessEngine? {
        val pack = registry.find(packId)?.takeIf { registry.isSupported(it) } ?: registry.defaultPack()
        val engine = pack?.let { registry.create(it) }
        if (engine != null) {
            active.add(engine)
            return engine
        }
        val fallback = SimpleEngine()
        fallback.start()
        active.add(fallback)
        return fallback
    }

    fun release(engine: ChessEngine) {
        runCatching { engine.close() }
        active.remove(engine)
    }

    fun closeAll() {
        active.forEach { engine -> runCatching { engine.close() } }
        active.clear()
    }
}
