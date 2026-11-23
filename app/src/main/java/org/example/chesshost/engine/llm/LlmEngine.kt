package org.example.chesshost.engine.llm

import android.util.Log
import org.example.chesshost.chess.Board
import org.example.chesshost.chess.Color
import org.example.chesshost.chess.Move
import org.example.chesshost.chess.RuleSet
import org.example.chesshost.chess.Rules
import org.example.chesshost.engine.ChessEngine
import org.example.chesshost.engine.EngineConfig
import org.example.chesshost.engine.EngineEval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

/**
 * Gegner über eine OpenAI-kompatible Chat-API.
 *
 * Die Engine rechnet nicht, sie fragt: Im Prompt stehen Stellung, Zugliste und
 * **alle legalen Züge** in UCI- und SAN-Schreibweise. Die Antwort wird gegen
 * diese Liste geprüft – ein Modell kann also keinen illegalen Zug spielen,
 * sondern höchstens keinen liefern. Bei einer unbrauchbaren Antwort wird genau
 * einmal mit einer Korrektur nachgefragt.
 *
 * Eine Instanz hält keinen Zustand zwischen den Zügen; sie kann deshalb wie
 * jede andere Engine in Partie, Duell und Turnier wiederverwendet werden.
 */
class LlmEngine(
    override val displayName: String,
    private val config: LlmConfig,
) : ChessEngine {

    @Volatile
    private var connection: HttpURLConnection? = null

    override suspend fun start(): Boolean = config.isConfigured

    override suspend fun bestMove(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): String? = withContext(Dispatchers.IO) {
        val rules = Rules.byId(config.variant)
        val board = runCatching { Board.fromFen(currentFen, rules) }.getOrNull()
            ?: return@withContext null
        val legal = board.legalMoves()
        if (legal.isEmpty()) return@withContext null

        val messages = mutableListOf(
            ChatMessage("system", systemPrompt()),
            ChatMessage("user", positionPrompt(board, rules, legal, uciHistory, config.skillLevel)),
        )

        repeat(MAX_ATTEMPTS) { attempt ->
            val reply = try {
                LlmClient.complete(this@LlmEngine.config, messages.toList()) { opened ->
                    connection = opened
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                // Netzfehler, Timeout, falscher Schlüssel: kein Zug.
                Log.w(TAG, "LLM-Anfrage fehlgeschlagen", t)
                return@withContext null
            } finally {
                connection = null
            }

            extractMove(reply, board, rules)?.let { return@withContext rules.toEngineMove(it) }
            if (attempt < MAX_ATTEMPTS - 1) {
                messages += ChatMessage("assistant", reply.take(500))
                messages += ChatMessage("user", RETRY_PROMPT)
            }
        }
        null
    }

    /** Die Chat-API liefert keine numerische Bewertung. */
    override suspend fun evaluate(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): EngineEval? = null

    override suspend fun stopSearch() {
        connection?.disconnect()
    }

    override fun close() {
        connection?.disconnect()
    }

    private fun systemPrompt(): String =
        "You are a chess engine. Always answer with exactly one legal move in UCI " +
            "notation (for example e2e4 or e7e8q) and nothing else. Never explain."

    private fun positionPrompt(
        board: Board,
        rules: RuleSet,
        legal: List<Move>,
        uciHistory: List<String>,
        skillLevel: Int,
    ): String = buildString {
        append("Game: ").append(rules.name).append('\n')
        append("FEN: ").append(board.fen()).append('\n')
        append("Side to move: ")
        append(if (board.sideToMove == Color.WHITE) "White" else "Black").append('\n')
        append("Moves so far (UCI): ")
        append(uciHistory.takeLast(40).joinToString(" ").ifEmpty { "none" }).append('\n')
        append("Play strength: level ")
        append(skillLevel.coerceIn(0, 20))
        append(" of 20 (0 = beginner, 20 = maximum).\n")
        append("Legal moves (UCI (SAN)):\n")
        append(legal.joinToString(", ") { move -> "${rules.toEngineMove(move)} (${rules.san(board, move, legal)})" })
        append("\nChoose exactly one move from the list and reply with its UCI notation only.")
    }

    companion object {
        private const val TAG = "LlmEngine"

        /** Eine Nachfrage, falls die erste Antwort keinen legalen Zug enthält. */
        private const val MAX_ATTEMPTS = 2

        private const val RETRY_PROMPT =
            "That answer contained no legal move. Reply with exactly one UCI move from the " +
                "legal moves list above, for example e2e4. No explanation."
    }
}

/** Findet den ersten legalen Zug in einer Modellantwort (UCI zuerst, dann SAN). */
internal fun extractMove(reply: String, board: Board, rules: RuleSet): Move? {
    val legal = board.legalMoves()
    if (legal.isEmpty()) return null
    val text = reply.lowercase()

    UCI_TOKEN.findAll(text).forEach { match ->
        val token = match.value
        legal.firstOrNull {
            rules.toEngineMove(it).equals(token, ignoreCase = true) || it.uci().equals(token, ignoreCase = true)
        }?.let { return it }
    }

    // SAN ist die zweite Chance, weil viele Modelle "Nf3" statt "g1f3" schreiben.
    legal
        .map { move -> move to rules.san(board, move, legal).lowercase().trimEnd('+', '#') }
        .sortedByDescending { (_, san) -> san.length }
        .forEach { (move, san) ->
            if (san.isNotEmpty() && containsToken(text, san)) return move
        }
    return null
}

/** Prüft, ob [token] in [text] als eigenes Wort steht (nicht in "matte" für "a3"). */
private fun containsToken(text: String, token: String): Boolean {
    var index = text.indexOf(token)
    while (index >= 0) {
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + token.length)
        if ((before == null || !before.isLetterOrDigit()) &&
            (after == null || !after.isLetterOrDigit())
        ) {
            return true
        }
        index = text.indexOf(token, index + 1)
    }
    return false
}

private val UCI_TOKEN = Regex("(?<![a-z0-9])([a-h][1-8][a-h][1-8][qrbn]?)(?![a-z0-9])")
