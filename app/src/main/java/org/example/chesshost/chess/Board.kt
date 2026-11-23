package org.example.chesshost.chess

import kotlin.math.abs

/**
 * Schachbrett mit Figuren, Rochaderechten, En-passant-Feld und Zählern.
 *
 * Das Brett ist veränderlich; [makeMove] liefert ein [BoardUndo], mit dem der
 * Zug exakt rückgängig gemacht werden kann. Das wird für die Legalitätsprüfung,
 * die Suche und für Perft-Tests genutzt.
 *
 * Welche Züge erlaubt sind, entscheidet das [RuleSet] des Bretts. Dadurch teilt
 * sich das Brett Standard-Schach und Schach960; ein anderes Regelwerk (etwa
 * Räuberschach) bringt einfach eine eigene Implementierung mit.
 */
class Board private constructor(
    private val squares: Array<Piece?>,
    var sideToMove: Color,
    var castling: Castling,
    var enPassantSquare: Int,
    var halfmoveClock: Int,
    var fullmoveNumber: Int,
    val rules: RuleSet,
) {

    fun pieceAt(square: Int): Piece? = squares[square]

    fun copy(): Board = Board(
        squares = squares.copyOf(),
        sideToMove = sideToMove,
        castling = castling.copy(),
        enPassantSquare = enPassantSquare,
        halfmoveClock = halfmoveClock,
        fullmoveNumber = fullmoveNumber,
        rules = rules,
    )

    /** Alle Felder als Kopie (für die Darstellung). */
    fun snapshot(): List<Piece?> = squares.toList()

    /** Königsfeld der Farbe oder -1, wenn kein König auf dem Brett steht. */
    fun kingSquare(color: Color): Int {
        for (i in 0 until 64) {
            val p = squares[i] ?: continue
            if (p.type == PieceType.KING && p.color == color) return i
        }
        return -1
    }

    fun isInCheck(color: Color): Boolean = rules.isInCheck(this, color)

    fun isAttacked(square: Int, by: Color): Boolean {
        val f = fileOf(square)
        val r = rankOf(square)

        // Bauern: ein weißer Bauer greift von Reihe-1 aus an, ein schwarzer von Reihe+1.
        val pawnRank = if (by == Color.WHITE) r - 1 else r + 1
        if (pawnRank in 0..7) {
            for (df in intArrayOf(-1, 1)) {
                val pf = f + df
                if (pf !in 0..7) continue
                val p = squares[pawnRank * 8 + pf]
                if (p != null && p.color == by && p.type == PieceType.PAWN) return true
            }
        }

        // Springer
        for ((df, dr) in KNIGHT_OFFSETS) {
            val nf = f + df
            val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val p = squares[nr * 8 + nf]
            if (p != null && p.color == by && p.type == PieceType.KNIGHT) return true
        }

        // König
        for ((df, dr) in KING_OFFSETS) {
            val nf = f + df
            val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val p = squares[nr * 8 + nf]
            if (p != null && p.color == by && p.type == PieceType.KING) return true
        }

        // Türme und Dame
        for ((df, dr) in ROOK_DIRECTIONS) {
            var nf = f + df
            var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val p = squares[nr * 8 + nf]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                nf += df
                nr += dr
            }
        }

        // Läufer und Dame
        for ((df, dr) in BISHOP_DIRECTIONS) {
            var nf = f + df
            var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val p = squares[nr * 8 + nf]
                if (p != null) {
                    if (p.color == by && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                nf += df
                nr += dr
            }
        }

        return false
    }

    /** Alle legalen Züge der Seite am Zug. */
    fun legalMoves(): List<Move> = rules.legalMoves(this)

    fun hasLegalMove(): Boolean = rules.legalMoves(this).isNotEmpty()

    /** Führt den Zug aus und liefert die Daten zum Rückgängigmachen. */
    fun makeMove(move: Move): BoardUndo {
        val mover = squares[move.from] ?: error("Kein Stein auf ${squareName(move.from)}")
        val castleRookPiece = if (move.isCastle) squares[move.castleRook] else null
        val captureSquare = when {
            move.isCastle -> -1
            move.enPassant -> if (mover.color == Color.WHITE) move.to - 8 else move.to + 8
            else -> move.to
        }
        val captured = if (captureSquare >= 0) squares[captureSquare] else null
        val rookTo = if (move.isCastle) {
            val rank = rankOf(move.from)
            rank * 8 + if (fileOf(move.to) == 6) 5 else 3
        } else {
            -1
        }

        val undo = BoardUndo(
            move = move,
            mover = mover,
            captured = captured,
            captureSquare = captureSquare,
            castling = castling.copy(),
            enPassantSquare = enPassantSquare,
            halfmoveClock = halfmoveClock,
            fullmoveNumber = fullmoveNumber,
            sideToMove = sideToMove,
            castleRook = castleRookPiece,
        )

        squares[move.from] = null
        if (move.isCastle) squares[move.castleRook] = null
        if (captured != null) squares[captureSquare] = null
        squares[move.to] = if (move.promotion != null) Piece(move.promotion, mover.color) else mover
        if (move.isCastle) squares[rookTo] = castleRookPiece

        if (mover.type == PieceType.KING) {
            castling = castling.withoutColor(mover.color)
        }
        castling = castling.withoutSquares(move.from, captureSquare)

        enPassantSquare = if (mover.type == PieceType.PAWN && abs(move.to - move.from) == 16) {
            (move.to + move.from) / 2
        } else {
            -1
        }

        if (mover.type == PieceType.PAWN || captured != null || move.enPassant) {
            halfmoveClock = 0
        } else {
            halfmoveClock++
        }
        if (mover.color == Color.BLACK) fullmoveNumber++
        sideToMove = mover.color.opposite

        return undo
    }

    fun undoMove(undo: BoardUndo) {
        val move = undo.move
        squares[move.to] = null
        if (move.isCastle) {
            val rank = rankOf(move.from)
            val rookTo = rank * 8 + if (fileOf(move.to) == 6) 5 else 3
            squares[rookTo] = null
        }
        squares[move.from] = undo.mover
        if (move.isCastle) squares[move.castleRook] = undo.castleRook
        if (undo.captured != null) squares[undo.captureSquare] = undo.captured

        castling = undo.castling
        enPassantSquare = undo.enPassantSquare
        halfmoveClock = undo.halfmoveClock
        fullmoveNumber = undo.fullmoveNumber
        sideToMove = undo.sideToMove
    }

    /** Stellung als FEN-String (inkl. Zähler). */
    fun fen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val piece = squares[rank * 8 + file]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) {
                        sb.append(empty)
                        empty = 0
                    }
                    sb.append(piece.fenChar)
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ').append(if (sideToMove == Color.WHITE) 'w' else 'b').append(' ')
        sb.append(rules.castlingField(this))
        sb.append(' ').append(if (enPassantSquare >= 0) squareName(enPassantSquare) else "-")
        sb.append(' ').append(halfmoveClock)
        sb.append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    /** FEN ohne Zugzähler – Grundlage für die Wiederholungszählung. */
    fun positionKey(): String {
        val parts = fen().split(' ')
        return "${parts[0]} ${parts[1]} ${parts[2]} ${parts[3]}"
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(rules: RuleSet = Rules.standard): Board = fromFen(START_FEN, rules)

        fun empty(rules: RuleSet = Rules.standard): Board = fromPieces(arrayOfNulls(64), Color.WHITE, Castling.none(), rules = rules)

        /**
         * Brett aus beliebiger Figurenverteilung – Grundlage für den
         * Stellungseditor. Geprüft wird hier nicht; das macht
         * [RuleSet.validate].
         */
        fun fromPieces(
            squares: Array<Piece?>,
            sideToMove: Color,
            castling: Castling,
            enPassantSquare: Int = -1,
            halfmoveClock: Int = 0,
            fullmoveNumber: Int = 1,
            rules: RuleSet = Rules.standard,
        ): Board = Board(
            squares = squares.copyOf(),
            sideToMove = sideToMove,
            castling = castling,
            enPassantSquare = enPassantSquare,
            halfmoveClock = halfmoveClock,
            fullmoveNumber = fullmoveNumber,
            rules = rules,
        )

        fun fromFen(fen: String, rules: RuleSet = Rules.standard): Board {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "Ungültiges FEN: $fen" }
            val ranks = parts[0].split('/')
            require(ranks.size == 8) { "Ungültiges FEN: $fen" }

            val squares = arrayOfNulls<Piece>(64)
            for ((rowIndex, row) in ranks.withIndex()) {
                val rank = 7 - rowIndex
                var file = 0
                for (char in row) {
                    if (char.isDigit()) {
                        file += char.digitToInt()
                    } else {
                        require(file in 0..7) { "Ungültiges FEN: $fen" }
                        val color = if (char.isUpperCase()) Color.WHITE else Color.BLACK
                        val type = when (char.uppercaseChar()) {
                            'P' -> PieceType.PAWN
                            'N' -> PieceType.KNIGHT
                            'B' -> PieceType.BISHOP
                            'R' -> PieceType.ROOK
                            'Q' -> PieceType.QUEEN
                            'K' -> PieceType.KING
                            else -> error("Ungültiges FEN: $fen")
                        }
                        squares[rank * 8 + file] = Piece(type, color)
                        file++
                    }
                }
                require(file == 8) { "Ungültiges FEN: $fen" }
            }

            val castling = parseCastling(parts[2], squares)

            val ep = if (parts[3] == "-") {
                -1
            } else {
                val file = parts[3][0] - 'a'
                val rank = parts[3][1] - '1'
                if (file in 0..7 && rank in 0..7) rank * 8 + file else -1
            }

            return Board(
                squares = squares,
                sideToMove = if (parts[1] == "b") Color.BLACK else Color.WHITE,
                castling = castling,
                enPassantSquare = ep,
                halfmoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0,
                fullmoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1,
                rules = rules,
            )
        }

        /**
         * Rochaderechte lesen. Unterstützt beide Schreibweisen:
         * `KQkq` (Standard bzw. X-FEN bei Schach960) und die Dateibuchstaben
         * der Shredder-FEN (`HAha`), die für Schach960 eindeutig ist.
         */
        private fun parseCastling(field: String, squares: Array<Piece?>): Castling {
            if (field == "-" || field.isEmpty()) return Castling.none()
            var castling = Castling.none()

            for (char in field) {
                when (char) {
                    'K', 'Q', 'k', 'q' -> {
                        val color = if (char.isUpperCase()) Color.WHITE else Color.BLACK
                        val kingSide = char.uppercaseChar() == 'K'
                        val rank = if (color == Color.WHITE) 0 else 7
                        val king = findPiece(squares, rank, PieceType.KING, color) ?: continue
                        val rook = outermostRook(squares, rank, color, kingSide) ?: continue
                        castling = castling.withRight(color, kingSide, rook, king)
                    }
                    in 'A'..'H', in 'a'..'z' -> {
                        val file = char.uppercaseChar() - 'A'
                        if (file !in 0..7) continue
                        val color = if (char.isUpperCase()) Color.WHITE else Color.BLACK
                        val rank = if (color == Color.WHITE) 0 else 7
                        val rookSquare = rank * 8 + file
                        val rook = squares[rookSquare]
                        if (rook == null || rook.type != PieceType.ROOK || rook.color != color) continue
                        val king = findPiece(squares, rank, PieceType.KING, color) ?: continue
                        castling = castling.withRight(color, file > fileOf(king), rookSquare, king)
                    }
                }
            }
            return castling
        }

        private fun findPiece(squares: Array<Piece?>, rank: Int, type: PieceType, color: Color): Int? {
            for (file in 0..7) {
                val square = rank * 8 + file
                val piece = squares[square]
                if (piece != null && piece.type == type && piece.color == color) return square
            }
            return null
        }

        /** Äußerster Turm einer Seite (X-FEN-Lesart von `KQkq` bei Schach960). */
        private fun outermostRook(
            squares: Array<Piece?>,
            rank: Int,
            color: Color,
            kingSide: Boolean,
        ): Int? {
            val files = if (kingSide) (7 downTo 0) else (0..7)
            for (file in files) {
                val square = rank * 8 + file
                val piece = squares[square]
                if (piece != null && piece.type == PieceType.ROOK && piece.color == color) return square
            }
            return null
        }

        private val KNIGHT_OFFSETS = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1,
        )
        private val KING_OFFSETS = listOf(
            -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 1,
            1 to -1, 1 to 0, 1 to 1,
        )
        private val ROOK_DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val BISHOP_DIRECTIONS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    }
}

/**
 * Rochaderechte als Turmfelder. Ein Recht besteht genau dann, wenn ein
 * Turmfeld eingetragen ist; Schach960 unterscheidet sich nur dadurch, dass
 * König und Türme nicht auf e1/h1/a1 stehen müssen.
 */
class Castling(
    private val kingSquares: IntArray = intArrayOf(-1, -1),
    private val rookSquares: IntArray = intArrayOf(-1, -1, -1, -1),
) {

    fun copy(): Castling = Castling(kingSquares.copyOf(), rookSquares.copyOf())

    fun has(color: Color, kingSide: Boolean): Boolean = rookSquares[index(color, kingSide)] >= 0

    fun kingFrom(color: Color): Int = kingSquares[color.ordinal]

    fun rookFrom(color: Color, kingSide: Boolean): Int = rookSquares[index(color, kingSide)]

    fun withRight(color: Color, kingSide: Boolean, rookSquare: Int, kingSquare: Int): Castling {
        val kings = kingSquares.copyOf()
        val rooks = rookSquares.copyOf()
        kings[color.ordinal] = kingSquare
        rooks[index(color, kingSide)] = rookSquare
        return Castling(kings, rooks)
    }

    fun withoutRight(color: Color, kingSide: Boolean): Castling {
        val rooks = rookSquares.copyOf()
        rooks[index(color, kingSide)] = -1
        return Castling(kingSquares.copyOf(), rooks)
    }

    fun withoutColor(color: Color): Castling {
        val kings = kingSquares.copyOf()
        val rooks = rookSquares.copyOf()
        kings[color.ordinal] = -1
        rooks[index(color, true)] = -1
        rooks[index(color, false)] = -1
        return Castling(kings, rooks)
    }

    /** Entfernt Rechte, deren König oder Turm das Feld verlassen hat. */
    fun withoutSquares(vararg squares: Int): Castling {
        val kings = kingSquares.copyOf()
        val rooks = rookSquares.copyOf()
        for (square in squares) {
            if (square < 0) continue
            for (color in Color.entries) {
                if (kings[color.ordinal] == square) {
                    kings[color.ordinal] = -1
                    rooks[index(color, true)] = -1
                    rooks[index(color, false)] = -1
                }
                for (kingSide in booleanArrayOf(true, false)) {
                    if (rooks[index(color, kingSide)] == square) rooks[index(color, kingSide)] = -1
                }
            }
        }
        return Castling(kings, rooks)
    }

    companion object {
        fun standard(): Castling = Castling(
            kingSquares = intArrayOf(4, 60),
            rookSquares = intArrayOf(7, 0, 63, 56),
        )

        fun none(): Castling = Castling()

        private fun index(color: Color, kingSide: Boolean): Int =
            color.ordinal * 2 + if (kingSide) 0 else 1
    }
}

/** Rückgängig-Daten für [Board.undoMove]. */
class BoardUndo internal constructor(
    val move: Move,
    val mover: Piece,
    val captured: Piece?,
    val captureSquare: Int,
    val castling: Castling,
    val enPassantSquare: Int,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    val sideToMove: Color,
    val castleRook: Piece?,
)
