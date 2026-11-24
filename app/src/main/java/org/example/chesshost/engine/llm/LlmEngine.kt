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
 * Die Engine rechnet nicht, sie fragt: Im Prompt stehen die Stellung als FEN,
 * die Partie bis hierher in SAN, ein Brettdiagramm und **alle legalen Züge als
 * nummerierte Liste**. Die Antwort wird gegen genau diese Liste geprüft – ein
 * Modell kann also keinen illegalen Zug spielen, sondern höchstens keinen
 * liefern. Bei einer unbrauchbaren Antwort wird mit einer kürzeren Anweisung
 * nachgefragt.
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
            ChatMessage(
                "user",
                buildPositionPrompt(board, rules, legal, uciHistory, startFen, config.skillLevel),
            ),
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

            parseChoice(reply, board, rules)?.let { return@withContext rules.toEngineMove(it) }
            if (attempt < MAX_ATTEMPTS - 1) {
                messages += ChatMessage("assistant", reply.take(500))
                messages += ChatMessage("user", retryPrompt(legal.size, attempt))
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
        "You are a chess engine. You are given a numbered list of all legal moves. " +
            "Always answer with the number of exactly one move from that list, in the form " +
            "\"MOVE: 12\" and nothing else. Never explain, never invent a move."

    companion object {
        private const val TAG = "LlmEngine"

        /** Zwei Nachfragen, falls die Antwort keinen legalen Zug enthält. */
        private const val MAX_ATTEMPTS = 3

        private fun retryPrompt(legalCount: Int, attempt: Int): String = if (attempt == 0) {
            "Your answer did not name one of the numbered legal moves. Reply with the number " +
                "of your chosen move only, for example: MOVE: 7"
        } else {
            "Still not a legal move. The only valid answers are the numbers 1 to $legalCount. " +
                "Reply with exactly one number in the form: MOVE: <number>"
        }
    }
}

/**
 * Baut die Nutzernachricht: Partieverlauf, Brett, Stellung und die nummerierte
 * Liste aller legalen Züge.
 *
 * Die Liste ist der Kern: Das Modell muss keine Zugnotation schreiben, sondern
 * nur eine Nummer abschreiben. Dadurch ist eine halluzinierte Antwort sofort
 * als solche erkennbar.
 */
internal fun buildPositionPrompt(
    board: Board,
    rules: RuleSet,
    legal: List<Move>,
    uciHistory: List<String>,
    startFen: String,
    skillLevel: Int,
): String = buildString {
    append("Game: ").append(rules.name).append('\n')
    append("You play: ")
    append(if (board.sideToMove == Color.WHITE) "White" else "Black").append('\n')

    val history = sanHistory(startFen, uciHistory, rules)
    append("Moves so far (SAN): ")
    if (history.isEmpty()) {
        append("none, this is the starting position")
    } else {
        append(history.joinToString(" "))
    }
    append('\n')

    append("Position (uppercase = White, lowercase = Black):\n")
    append(boardDiagram(board)).append('\n')
    append("FEN: ").append(board.fen()).append('\n')

    append("Play strength: level ")
    append(skillLevel.coerceIn(0, 20))
    append(" of 20 (0 = beginner, 20 = maximum). Lower levels may pick a weaker move.\n")

    append("Legal moves for ")
    append(if (board.sideToMove == Color.WHITE) "White" else "Black")
    append(", numbered:\n")
    legal.forEachIndexed { index, move ->
        append((index + 1).toString().padStart(2, ' ')).append(". ")
        append(rules.toEngineMove(move)).append(" (").append(rules.san(board, move, legal)).append(")\n")
    }
    append("Answer with the number of your chosen move, exactly like: MOVE: 1")
}

/**
 * Partieverlauf als SAN mit Zugnummern (PGN-Stil), aus der Startstellung
 * nachgespielt. Lässt sich der Verlauf nicht vollständig nachspielen, wird eine
 * leere Liste geliefert; der Prompt zeigt dann nur FEN und Brett.
 */
internal fun sanHistory(startFen: String, uciHistory: List<String>, rules: RuleSet): List<String> {
    if (uciHistory.isEmpty()) return emptyList()
    val board = runCatching { Board.fromFen(startFen, rules) }.getOrNull() ?: return emptyList()
    val parts = ArrayList<String>(uciHistory.size + 8)
    for (uci in uciHistory) {
        val legal = board.legalMoves()
        val move = legal.firstOrNull {
            rules.toEngineMove(it).equals(uci, ignoreCase = true) ||
                it.uci().equals(uci, ignoreCase = true)
        } ?: return emptyList()
        if (board.sideToMove == Color.WHITE) parts += "${board.fullmoveNumber}."
        parts += rules.san(board, move, legal)
        board.makeMove(move)
    }
    // Beginnt der Verlauf mit Schwarz (eigene Startstellung), fehlt die Nummer.
    if (parts.firstOrNull()?.endsWith('.') == false) {
        parts.add(0, "${uciHistoryStartNumber(startFen, rules)}...")
    }
    return parts
}

private fun uciHistoryStartNumber(startFen: String, rules: RuleSet): Int =
    runCatching { Board.fromFen(startFen, rules).fullmoveNumber }.getOrDefault(1)

/** Brett als Text, damit auch Modelle ohne FEN-Verständnis die Stellung sehen. */
internal fun boardDiagram(board: Board): String = buildString {
    val squares = board.snapshot()
    for (rank in 7 downTo 0) {
        append(rank + 1).append(' ')
        for (file in 0..7) {
            val piece = squares[rank * 8 + file]
            append(if (piece == null) '.' else piece.fenChar)
            if (file < 7) append(' ')
        }
        append('\n')
    }
    append("  a b c d e f g h")
}

/**
 * Sucht den gewählten Zug in einer Modellantwort.
 *
 * Reihenfolge: ausdrückliche Nummer (`MOVE: 7`), eine Antwort, die nur aus einer
 * Nummer besteht, dann UCI und zuletzt SAN als Rückfallebene.
 */
internal fun parseChoice(reply: String, board: Board, rules: RuleSet): Move? {
    val legal = board.legalMoves()
    if (legal.isEmpty()) return null

    // Nennt das Modell ausdrücklich eine Nummer, zählt nur diese.
    MOVE_NUMBER.find(reply)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
        return legal.getOrNull(number - 1)
    }

    val numbers = Regex("\\d+").findAll(reply).map { it.value }.distinct().toList()
    if (numbers.size == 1) {
        numbers[0].toIntOrNull()?.let { number -> legal.getOrNull(number - 1)?.let { return it } }
    }

    return extractMove(reply, board, rules)
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

private val MOVE_NUMBER = Regex("(?i)\\bmove\\s*[:=]?\\s*(\\d+)")
private val UCI_TOKEN = Regex("(?<![a-z0-9])([a-h][1-8][a-h][1-8][qrbn]?)(?![a-z0-9])")
