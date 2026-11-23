package org.example.schach.engine.llm

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Eine Nachricht im Chat-Format (OpenAI-kompatibel). */
data class ChatMessage(val role: String, val content: String)

/** Fehler der Gegenseite: HTTP-Status oder unbrauchbare Antwort. */
class LlmException(message: String) : IOException(message)

/**
 * Kleiner Client für `/chat/completions`. Bewusst ohne zusätzliche
 * Bibliothek: Die App nutzt für Katalog und Downloads ebenfalls
 * [HttpURLConnection].
 */
object LlmClient {

    /**
     * Schickt die Nachrichten an das Modell und liefert den Text der ersten
     * Antwort.
     *
     * @param onConnection wird mit der offenen Verbindung aufgerufen, damit
     *   eine laufende Anfrage abgebrochen werden kann (`disconnect`).
     */
    fun complete(
        config: LlmConfig,
        messages: List<ChatMessage>,
        timeoutMs: Long = config.readTimeoutMs,
        onConnection: (HttpURLConnection) -> Unit = {},
    ): String {
        val connection = URL(config.endpoint).openConnection() as HttpURLConnection
        onConnection(connection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = timeoutMs.coerceIn(1_000L, 300_000L).toInt()
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            // Bewusst nur Modell und Nachrichten: Manche Anbieter lehnen
            // Zusatzparameter wie temperature ab.
            val body = JSONObject().apply {
                put("model", config.model.trim())
                put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach { message ->
                            put(
                                JSONObject()
                                    .put("role", message.role)
                                    .put("content", message.content),
                            )
                        }
                    },
                )
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw LlmException(errorMessage(code, text))

            val content = contentOf(text)
            if (content.isBlank()) throw LlmException("Das Modell hat keine Antwort geliefert.")
            return content
        } finally {
            connection.disconnect()
        }
    }

    private fun contentOf(text: String): String = runCatching {
        val choice = JSONObject(text).optJSONArray("choices")?.optJSONObject(0)
        choice?.optJSONObject("message")?.optString("content").orEmpty()
    }.getOrDefault("")

    /** Liest die Fehlermeldung der API, sonst den HTTP-Status. */
    private fun errorMessage(code: Int, text: String): String {
        val detail = runCatching {
            JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val message = detail.ifBlank { text }.trim().replace(Regex("\\s+"), " ")
        if (message.isEmpty()) return "HTTP $code"
        val short = if (message.length > 300) message.take(300) + "…" else message
        return "HTTP $code: $short"
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
}
