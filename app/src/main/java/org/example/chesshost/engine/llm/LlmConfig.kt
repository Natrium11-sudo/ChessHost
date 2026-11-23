package org.example.chesshost.engine.llm

import org.example.chesshost.engine.packs.EngineBackend
import org.example.chesshost.engine.packs.EngineOrigin
import org.example.chesshost.engine.packs.EnginePack

/**
 * Zugang zu einem Sprachmodell über eine OpenAI-kompatible Chat-API.
 *
 * Die Adresse ist die Basis des Endpunkts (also ohne `/chat/completions`),
 * damit sowohl OpenAI als auch lokale Server (Ollama, LM Studio, vLLM …)
 * funktionieren. Ein Schlüssel ist optional, weil lokale Server oft keinen
 * verlangen.
 */
data class LlmConfig(
    val displayName: String = "LLM",
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    val timeoutSeconds: Int = 60,
) {

    /** Anzeigename bei der Gegnerwahl; ohne eigenen Namen der Modellname. */
    val packName: String
        get() = displayName.trim().ifEmpty { model.trim().ifEmpty { "LLM" } }

    /** Erst mit Adresse und Modell ist der Gegner nutzbar. */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    val endpoint: String
        get() = baseUrl.trim().trimEnd('/') + "/chat/completions"

    val readTimeoutMs: Long
        get() = timeoutSeconds.coerceIn(5, 300) * 1000L

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

/** ID des synthetischen Engine-Packs, das das LLM in allen Auswahlen vertritt. */
const val LLM_PACK_ID = "llm"

/**
 * Baut den Katalog-Eintrag für den LLM-Gegner. Er wird nicht aus den Assets
 * geladen, sondern bei jedem Zugriff aus den Einstellungen erzeugt – dadurch
 * ändert sich der Anzeigename sofort mit der Konfiguration.
 */
fun llmEnginePack(config: LlmConfig): EnginePack = EnginePack(
    id = LLM_PACK_ID,
    name = config.packName,
    version = "",
    backend = EngineBackend.LLM,
    origin = EngineOrigin.BUILTIN,
    description = "Spielt über eine OpenAI-kompatible Chat-API. Adresse, Modell und " +
        "Schlüssel werden in der App hinterlegt.",
    elo = null,
    license = "",
    homepage = null,
    sourceUrl = null,
    tags = listOf("LLM", "API"),
    variants = emptyList(),
    binary = null,
    executable = null,
    uci = null,
    files = emptyList(),
    minAppVersion = 1,
    available = true,
    note = null,
)
