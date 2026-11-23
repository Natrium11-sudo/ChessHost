package org.example.schach.engine.llm

import org.example.schach.engine.ChessEngine
import org.example.schach.engine.EngineProvider
import org.example.schach.engine.packs.EngineBackend
import org.example.schach.engine.packs.EnginePack

/**
 * Stellt den LLM-Gegner allen anderen Engines gleich. Der Zugriff auf die
 * Einstellungen läuft über eine Funktion, damit eine geänderte Konfiguration
 * sofort gilt, ohne die Registry neu zu bauen.
 */
class LlmEngineProvider(private val config: () -> LlmConfig) : EngineProvider {

    override val backend: EngineBackend = EngineBackend.LLM

    override fun isAvailable(pack: EnginePack): Boolean = config().isConfigured

    override suspend fun create(pack: EnginePack): ChessEngine? {
        val current = config()
        if (!current.isConfigured) return null
        return LlmEngine(current.packName, current)
    }
}
