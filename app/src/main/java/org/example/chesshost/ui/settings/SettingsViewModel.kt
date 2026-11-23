package org.example.chesshost.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.example.chesshost.App
import org.example.chesshost.data.Settings
import org.example.chesshost.data.ThemeMode
import org.example.chesshost.engine.Difficulty
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val repository = container.settingsRepository

    val settings: StateFlow<Settings> = repository.state

    val defaultEngineName: String =
        container.engineManager.defaultEngine()?.title ?: "Noch keine Engine aktiviert"

    val engineCount: Int = container.engineManager.engines().size

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    fun setDifficulty(difficulty: Difficulty) = repository.setDifficulty(difficulty)

    fun setShowCoordinates(enabled: Boolean) = repository.setShowCoordinates(enabled)

    fun setShowLegalMoves(enabled: Boolean) = repository.setShowLegalMoves(enabled)

    fun setHighlightLastMove(enabled: Boolean) = repository.setHighlightLastMove(enabled)

    fun setLanguage(code: String) = repository.setLanguageCode(code)

    fun setSoundEnabled(enabled: Boolean) = repository.setSoundEnabled(enabled)

    fun setHapticsEnabled(enabled: Boolean) = repository.setHapticsEnabled(enabled)
}
