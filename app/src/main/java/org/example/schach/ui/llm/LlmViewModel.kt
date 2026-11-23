package org.example.schach.ui.llm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.schach.App
import org.example.schach.data.Settings
import org.example.schach.engine.llm.ChatMessage
import org.example.schach.engine.llm.LlmClient
import org.example.schach.engine.llm.LlmConfig
import org.example.schach.ui.i18n.trNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ergebnis des Verbindungstests auf dem LLM-Screen. */
data class LlmTestState(
    val running: Boolean = false,
    val message: String? = null,
    val failed: Boolean = false,
)

class LlmViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as App).container.settingsRepository

    val settings: StateFlow<Settings> = repository.state

    private val _test = MutableStateFlow(LlmTestState())
    val test: StateFlow<LlmTestState> = _test.asStateFlow()

    fun save(config: LlmConfig) {
        repository.setLlm(config)
        _test.value = LlmTestState()
    }

    /** Schickt eine minimale Anfrage, damit Fehler vor der ersten Partie auffallen. */
    fun testConnection(config: LlmConfig) {
        if (_test.value.running) return
        if (!config.isConfigured) {
            _test.value = LlmTestState(
                message = trNow("API-Adresse und Modell fehlen."),
                failed = true,
            )
            return
        }
        _test.value = LlmTestState(running = true)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    LlmClient.complete(
                        config = config,
                        messages = listOf(ChatMessage("user", "Antworte mit dem Wort OK.")),
                        timeoutMs = config.readTimeoutMs,
                    )
                }
            }
            _test.value = result.fold(
                onSuccess = { reply ->
                    LlmTestState(
                        message = trNow(
                            "%s antwortet: %s",
                            config.model.trim(),
                            reply.trim().take(80),
                        ),
                    )
                },
                onFailure = { throwable ->
                    LlmTestState(
                        message = throwable.message ?: trNow("Verbindung fehlgeschlagen."),
                        failed = true,
                    )
                },
            )
        }
    }

}
