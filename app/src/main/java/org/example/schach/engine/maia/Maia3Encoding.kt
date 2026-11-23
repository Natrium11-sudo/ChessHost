package org.example.schach.engine.maia

import org.example.schach.chess.Board
import org.example.schach.chess.Color
import org.example.schach.chess.Move
import org.example.schach.chess.Piece
import org.example.schach.chess.PieceType

/**
 * Kodierung für Maia 3 (Chessformer), wie sie das Referenzprojekt
 * (CSSLab/maia-platform-frontend) verwendet.
 *
 * Das Modell erwartet:
 *  - `tokens` als (64, 12): je Feld ein Kanal pro Figur, Weiß zuerst
 *    (P, N, B, R, Q, K, p, n, b, r, q, k). Es gibt **keine** Kanäle für
 *    Rochaderechte, En passant oder die Zugfarbe – das Brett wird für Schwarz
 *    gespiegelt (Reihen vertauscht, Farben getauscht), damit immer Weiß „am
 *    Zug“ ist.
 *  - `elo_self` / `elo_oppo` als Spielstärke beider Seiten.
 *  - Ausgabe `logits_move` über 4352 Züge: `von * 64 + nach` für normale Züge,
 *    ab 4096 die Umwandlungen `4096 + (vonDatei * 8 + nachDatei) * 4 + Figur`
 *    mit Dame, Turm, Läufer, Springer in dieser Reihenfolge.
 *
 * Die Klasse kommt ohne Android-Abhängigkeiten aus und ist damit auf der JVM
 * testbar.
 */
object Maia3Encoding {

    const val BOARD_CHANNELS = 12
    const val MOVE_COUNT = 4352

    private const val PROMOTION_OFFSET = 4096

    /** Kanäle der Umwandlungsfiguren in der Reihenfolge des Modells. */
    private val PROMOTION_ORDER = listOf(
        PieceType.QUEEN,
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.KNIGHT,
    )

    /** Muss die Stellung für das Modell gespiegelt werden? */
    fun needsMirror(fen: String): Boolean = sideToMoveOf(fen) == Color.BLACK

    /**
     * Brett als (64, 12)-Tensor, zeilenweise: Feld × Kanal.
     * Bei Schwarz am Zug wird vorher gespiegelt.
     */
    fun tokens(fen: String): FloatArray {
        val board = Board.fromFen(if (needsMirror(fen)) mirrorFen(fen) else fen)
        val tokens = FloatArray(64 * BOARD_CHANNELS)
        for (square in 0 until 64) {
            val piece = board.pieceAt(square) ?: continue
            tokens[square * BOARD_CHANNELS + channelOf(piece)] = 1f
        }
        return tokens
    }

    /** Kanal einer Figur: Weiß 0..5, Schwarz 6..11. */
    fun channelOf(piece: Piece): Int =
        piece.type.ordinal + if (piece.color == Color.WHITE) 0 else 6

    /** Index eines Zugs im 4352er-Vokabular des Modells. */
    fun moveIndex(move: Move): Int {
        val promotion = move.promotion
        if (promotion == null) return move.from * 64 + move.to

        val pieceIndex = PROMOTION_ORDER.indexOf(promotion)
        require(pieceIndex >= 0) { "Unbekannte Umwandlungsfigur: $promotion" }
        val fromFile = move.from % 8
        val toFile = move.to % 8
        return PROMOTION_OFFSET + (fromFile * 8 + toFile) * 4 + pieceIndex
    }

    /**
     * Wählt den wahrscheinlichsten legalen Zug aus den Modell-Logits.
     * Gibt den Zug in UCI-Notation der **echten** Stellung zurück.
     */
    fun bestMove(fen: String, logits: FloatArray): String? {
        val mirrored = needsMirror(fen)
        val board = Board.fromFen(if (mirrored) mirrorFen(fen) else fen)
        val legal = board.legalMoves()
        if (legal.isEmpty()) return null

        var bestScore = Float.NEGATIVE_INFINITY
        var bestMove: Move? = null
        for (move in legal) {
            val index = moveIndex(move)
            if (index !in logits.indices) continue
            if (logits[index] > bestScore) {
                bestScore = logits[index]
                bestMove = move
            }
        }
        val uci = bestMove?.uci() ?: return null
        return if (mirrored) mirrorUci(uci) else uci
    }

    /** Spiegelt eine Stellung: Reihen vertauscht, Farben getauscht. */
    fun mirrorFen(fen: String): String {
        val parts = fen.trim().split(Regex("\\s+"))
        val position = parts.getOrNull(0) ?: return fen
        val active = parts.getOrNull(1) ?: "w"
        val castling = parts.getOrNull(2) ?: "-"
        val enPassant = parts.getOrNull(3) ?: "-"
        val halfmove = parts.getOrNull(4) ?: "0"
        val fullmove = parts.getOrNull(5) ?: "1"

        val mirroredPosition = position.split('/')
            .reversed()
            .joinToString("/") { rank -> swapColors(rank) }

        val mirroredActive = if (active == "w") "b" else "w"
        val mirroredEnPassant = if (enPassant == "-") "-" else mirrorSquare(enPassant)
        return "$mirroredPosition $mirroredActive ${mirrorCastling(castling)} " +
            "$mirroredEnPassant $halfmove $fullmove"
    }

    /** Spiegelt einen UCI-Zug senkrecht (Datei bleibt, Reihe wird gespiegelt). */
    fun mirrorUci(uci: String): String {
        if (uci.length < 4) return uci
        val promotion = if (uci.length > 4) uci.substring(4) else ""
        return mirrorSquare(uci.substring(0, 2)) +
            mirrorSquare(uci.substring(2, 4)) +
            promotion
    }

    fun mirrorSquare(square: String): String {
        if (square.length < 2) return square
        val rank = square[1].digitToIntOrNull() ?: return square
        return "${square[0]}${9 - rank}"
    }

    private fun swapColors(rank: String): String = buildString(rank.length) {
        for (char in rank) {
            append(
                when {
                    char.isUpperCase() -> char.lowercaseChar()
                    char.isLowerCase() -> char.uppercaseChar()
                    else -> char
                },
            )
        }
    }

    private fun mirrorCastling(castling: String): String {
        if (castling == "-" || castling.isEmpty()) return "-"
        val mirrored = buildString {
            if ('k' in castling) append('K')
            if ('q' in castling) append('Q')
            if ('K' in castling) append('k')
            if ('Q' in castling) append('q')
        }
        return mirrored.ifEmpty { "-" }
    }

    private fun sideToMoveOf(fen: String): Color =
        if (fen.trim().split(Regex("\\s+")).getOrNull(1) == "b") Color.BLACK else Color.WHITE
}
