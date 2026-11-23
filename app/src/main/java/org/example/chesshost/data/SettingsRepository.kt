package org.example.chesshost.data

import android.content.Context
import org.example.chesshost.chess.TimeControls
import org.example.chesshost.engine.Difficulty
import org.example.chesshost.engine.llm.LlmConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val difficulty: Difficulty = Difficulty.CLUB,
    val showCoordinates: Boolean = true,
    val showLegalMoves: Boolean = true,
    val highlightLastMove: Boolean = true,
    /** Engine-Gegner im Spiel gegen die Engine (Pack-ID, null = Standard). */
    val playEngineId: String? = null,
    /** Engines im Duell-Modus (Pack-IDs, null = Standard). */
    val whiteEngineId: String? = null,
    val blackEngineId: String? = null,
    /** Optionaler zweiter Katalog aus dem Netz, ergänzt den eingebauten. */
    val catalogUrl: String = "",
    /** Zuletzt gewählte Bedenkzeit (siehe `TimeControls`). */
    val timeControlId: String = TimeControls.default.id,
    /** Sprache der Oberfläche ("de", "en", "fr", "es"). */
    val languageCode: String = "de",
    /** Klang beim Ziehen. */
    val soundEnabled: Boolean = true,
    /** Kurze Vibration beim Ziehen. */
    val hapticsEnabled: Boolean = true,
    /** LLM-Gegner über eine OpenAI-kompatible Chat-API. */
    val llm: LlmConfig = LlmConfig(),
)

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("schach_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun load(): Settings = Settings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        difficulty = Difficulty.fromName(prefs.getString(KEY_DIFFICULTY, null)),
        showCoordinates = prefs.getBoolean(KEY_COORDINATES, true),
        showLegalMoves = prefs.getBoolean(KEY_LEGAL_MOVES, true),
        highlightLastMove = prefs.getBoolean(KEY_LAST_MOVE, true),
        playEngineId = prefs.getString(KEY_PLAY_ENGINE, null),
        whiteEngineId = prefs.getString(KEY_WHITE_ENGINE, null),
        blackEngineId = prefs.getString(KEY_BLACK_ENGINE, null),
        catalogUrl = prefs.getString(KEY_CATALOG_URL, "").orEmpty(),
        timeControlId = TimeControls.byId(prefs.getString(KEY_TIME_CONTROL, null)).id,
        languageCode = prefs.getString(KEY_LANGUAGE, "de") ?: "de",
        soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        llm = LlmConfig(
            displayName = prefs.getString(KEY_LLM_NAME, null) ?: LlmConfig().displayName,
            baseUrl = prefs.getString(KEY_LLM_URL, null) ?: LlmConfig.DEFAULT_BASE_URL,
            model = prefs.getString(KEY_LLM_MODEL, null) ?: LlmConfig.DEFAULT_MODEL,
            apiKey = prefs.getString(KEY_LLM_KEY, "").orEmpty(),
            timeoutSeconds = prefs.getInt(KEY_LLM_TIMEOUT, 60).coerceIn(5, 300),
        ),
    )

    fun setThemeMode(mode: ThemeMode) = update(KEY_THEME to mode.name) { it.copy(themeMode = mode) }

    fun setDifficulty(difficulty: Difficulty) =
        update(KEY_DIFFICULTY to difficulty.name) { it.copy(difficulty = difficulty) }

    fun setShowCoordinates(enabled: Boolean) =
        update(KEY_COORDINATES to enabled) { it.copy(showCoordinates = enabled) }

    fun setShowLegalMoves(enabled: Boolean) =
        update(KEY_LEGAL_MOVES to enabled) { it.copy(showLegalMoves = enabled) }

    fun setHighlightLastMove(enabled: Boolean) =
        update(KEY_LAST_MOVE to enabled) { it.copy(highlightLastMove = enabled) }

    fun setPlayEngineId(packId: String?) =
        updateNullable(KEY_PLAY_ENGINE, packId) { it.copy(playEngineId = packId) }

    fun setWhiteEngineId(packId: String?) =
        updateNullable(KEY_WHITE_ENGINE, packId) { it.copy(whiteEngineId = packId) }

    fun setBlackEngineId(packId: String?) =
        updateNullable(KEY_BLACK_ENGINE, packId) { it.copy(blackEngineId = packId) }

    fun setCatalogUrl(url: String) =
        update(KEY_CATALOG_URL to url.trim()) { it.copy(catalogUrl = url.trim()) }

    fun setTimeControlId(id: String) =
        update(KEY_TIME_CONTROL to TimeControls.byId(id).id) { it.copy(timeControlId = TimeControls.byId(id).id) }

    fun setLanguageCode(code: String) =
        update(KEY_LANGUAGE to code) { it.copy(languageCode = code) }

    fun setSoundEnabled(enabled: Boolean) =
        update(KEY_SOUND to enabled) { it.copy(soundEnabled = enabled) }

    fun setHapticsEnabled(enabled: Boolean) =
        update(KEY_HAPTICS to enabled) { it.copy(hapticsEnabled = enabled) }

    /** Speichert den LLM-Zugang; der Gegner erscheint danach in allen Auswahlen. */
    fun setLlm(config: LlmConfig) {
        val normalized = config.copy(
            displayName = config.displayName.trim(),
            baseUrl = config.baseUrl.trim(),
            model = config.model.trim(),
            apiKey = config.apiKey.trim(),
            timeoutSeconds = config.timeoutSeconds.coerceIn(5, 300),
        )
        prefs.edit()
            .putString(KEY_LLM_NAME, normalized.displayName)
            .putString(KEY_LLM_URL, normalized.baseUrl)
            .putString(KEY_LLM_MODEL, normalized.model)
            .putString(KEY_LLM_KEY, normalized.apiKey)
            .putInt(KEY_LLM_TIMEOUT, normalized.timeoutSeconds)
            .apply()
        _state.value = _state.value.copy(llm = normalized)
    }

    private fun update(entry: Pair<String, Any>, transform: (Settings) -> Settings) {
        val editor = prefs.edit()
        val (key, value) = entry
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
        }
        editor.apply()
        _state.value = transform(_state.value)
    }

    private fun updateNullable(key: String, value: String?, transform: (Settings) -> Settings) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
        _state.value = transform(_state.value)
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_COORDINATES = "show_coordinates"
        private const val KEY_LEGAL_MOVES = "show_legal_moves"
        private const val KEY_LAST_MOVE = "highlight_last_move"
        private const val KEY_PLAY_ENGINE = "play_engine_id"
        private const val KEY_WHITE_ENGINE = "white_engine_id"
        private const val KEY_BLACK_ENGINE = "black_engine_id"
        private const val KEY_CATALOG_URL = "catalog_url"
        private const val KEY_TIME_CONTROL = "time_control_id"
        private const val KEY_LANGUAGE = "language_code"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_LLM_NAME = "llm_display_name"
        private const val KEY_LLM_URL = "llm_base_url"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_LLM_KEY = "llm_api_key"
        private const val KEY_LLM_TIMEOUT = "llm_timeout_seconds"
    }
}
