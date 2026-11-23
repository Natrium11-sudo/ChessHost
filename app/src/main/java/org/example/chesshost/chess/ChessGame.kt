package org.example.chesshost.chess

import java.time.LocalDate

/** Ergebnis einer Partie aus Weiß-Sicht. */
enum class GameResult(val pgn: String) {
    WHITE_WINS("1-0"),
    BLACK_WINS("0-1"),
    DRAW("1/2-1/2");

    val label: String
        get() = when (this) {
            WHITE_WINS -> "Weiß gewinnt"
            BLACK_WINS -> "Schwarz gewinnt"
            DRAW -> "Remis"
        }
}

enum class EndReason(val label: String) {
    CHECKMATE("Matt"),
    STALEMATE("Patt"),
    FIFTY_MOVES("50-Züge-Regel"),
    THREEFOLD_REPETITION("Dreifache Stellungswiederholung"),
    INSUFFICIENT_MATERIAL("Unzureichendes Material"),
    RESIGNATION("Aufgabe"),
    TIMEOUT("Zeitablauf"),
    DRAW_AGREEMENT("Remis vereinbart"),
    ABORTED("Abgebrochen"),
}

data class GameEnd(val result: GameResult, val reason: EndReason) {
    val description: String
        get() = "${result.label} – ${reason.label}"
}

/**
 * Vollständige Partie: Brett, Zugliste inkl. SAN, Stellungswiederholungen und
 * Ergebnis. Alle Züge werden geprüft (legal) und können rückgängig gemacht werden.
 */
class ChessGame(
    val startFen: String = Board.START_FEN,
    val rules: RuleSet = Rules.standard,
) {

    var board: Board = Board.fromFen(startFen, rules)
        private set

    val moves: MutableList<Move> = mutableListOf()
    val sanMoves: MutableList<String> = mutableListOf()

    var end: GameEnd? = null
        private set

    private val snapshots = mutableListOf<Snapshot>()
    private val positionKeys = mutableListOf(board.positionKey())

    private data class Snapshot(val board: Board, val end: GameEnd?)

    val startColor: Color
        get() = boardFromFenSide(startFen)

    fun legalMoves(): List<Move> = board.legalMoves()

    fun fen(): String = board.fen()

    /** Alle bisherigen Züge in der Schreibweise, die die Engine erwartet. */
    fun uciHistory(): List<String> = moves.map { rules.toEngineMove(it) }

    /** Übersetzt einen Engine-Zug in einen legalen Zug dieser Partie. */
    fun parseEngineMove(uci: String?): Move? {
        if (uci.isNullOrBlank()) return null
        return rules.parseEngineMove(board, uci)
    }

    val isFinished: Boolean
        get() = end != null

    /** Führt einen legalen Zug aus. Gibt false zurück, wenn der Zug unzulässig ist. */
    fun makeMove(move: Move): Boolean {
        if (end != null) return false
        val legal = legalMoves()
        if (move !in legal) return false

        sanMoves.add(San.of(board, move, legal))
        snapshots.add(Snapshot(board.copy(), end))
        board.makeMove(move)
        moves.add(move)
        positionKeys.add(board.positionKey())
        end = detectEnd()
        return true
    }

    /** Nimmt den letzten Zug zurück. */
    fun undo(): Move? {
        val snapshot = snapshots.removeLastOrNull() ?: return null
        val move = moves.removeLastOrNull() ?: return null
        sanMoves.removeLastOrNull()
        positionKeys.removeLastOrNull()
        board = snapshot.board
        end = snapshot.end
        return move
    }

    fun resign(color: Color) {
        if (end != null) return
        val result = if (color == Color.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS
        end = GameEnd(result, EndReason.RESIGNATION)
    }

    fun agreeDraw() {
        if (end != null) return
        end = GameEnd(GameResult.DRAW, EndReason.DRAW_AGREEMENT)
    }

    fun timeout(loser: Color) {
        if (end != null) return
        val result = if (loser == Color.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS
        end = GameEnd(result, EndReason.TIMEOUT)
    }

    fun abort() {
        if (end != null) return
        end = GameEnd(GameResult.DRAW, EndReason.ABORTED)
    }

    private fun detectEnd(): GameEnd? = rules.endState(board, positionKeys)

    /** Zugpaare für die Anzeige, z. B. "1. e4 e5". */
    fun movePairs(): List<Pair<Int, Pair<String?, String?>>> {
        if (sanMoves.isEmpty()) return emptyList()
        val offset = boardFromFenMoveNumber(startFen)
        val startIsBlack = boardFromFenSide(startFen) == Color.BLACK
        val result = mutableListOf<Pair<Int, Pair<String?, String?>>>()
        var index = 0
        var number = offset

        if (startIsBlack) {
            result.add(number to (null to sanMoves[0]))
            index = 1
            number++
        }
        while (index < sanMoves.size) {
            val white = sanMoves.getOrNull(index)
            val black = sanMoves.getOrNull(index + 1)
            result.add(number to (white to black))
            index += 2
            number++
        }
        return result
    }

    /** Vollständige PGN-Ausgabe inkl. Kopfzeilen. */
    fun toPgn(
        whiteName: String,
        blackName: String,
        extraTags: Map<String, String> = emptyMap(),
        date: LocalDate = LocalDate.now(),
    ): String {
        val sb = StringBuilder()
        val tags = linkedMapOf(
            "Event" to "ChessHost",
            "Site" to "Android",
            "Date" to "%04d.%02d.%02d".format(date.year, date.monthValue, date.dayOfMonth),
            "Round" to "-",
            "White" to whiteName,
            "Black" to blackName,
            "Result" to (end?.result ?: GameResult.DRAW).pgn,
            "Termination" to (end?.reason?.label ?: "offen"),
        )
        rules.pgnVariant?.let { tags["Variant"] = it }
        if (startFen != Board.START_FEN) {
            tags["SetUp"] = "1"
            tags["FEN"] = startFen
        }
        tags.putAll(extraTags)
        for ((key, value) in tags) {
            sb.append('[').append(key).append(" \"").append(value.replace("\"", "\\\"")).append("\"]\n")
        }
        sb.append('\n')

        val body = StringBuilder()
        for ((number, pair) in movePairs()) {
            val prefix = "$number."
            val white = pair.first
            val black = pair.second
            if (white != null) {
                body.append(prefix).append(' ').append(white).append(' ')
            } else {
                body.append(prefix).append("... ")
            }
            if (black != null) body.append(black).append(' ')
        }
        body.append((end?.result ?: GameResult.DRAW).pgn)
        sb.append(wrap(body.toString().trim(), 80))
        sb.append('\n')
        return sb.toString()
    }

    private fun wrap(text: String, width: Int): String {
        val words = text.split(' ')
        val sb = StringBuilder()
        var lineLength = 0
        for (word in words) {
            if (lineLength > 0 && lineLength + word.length + 1 > width) {
                sb.append('\n')
                lineLength = 0
            } else if (lineLength > 0) {
                sb.append(' ')
                lineLength++
            }
            sb.append(word)
            lineLength += word.length
        }
        return sb.toString()
    }

    private fun boardFromFenSide(fen: String): Color {
        val parts = fen.trim().split(Regex("\\s+"))
        return if (parts.getOrNull(1) == "b") Color.BLACK else Color.WHITE
    }

    private fun boardFromFenMoveNumber(fen: String): Int {
        val parts = fen.trim().split(Regex("\\s+"))
        return parts.getOrNull(5)?.toIntOrNull() ?: 1
    }
}
