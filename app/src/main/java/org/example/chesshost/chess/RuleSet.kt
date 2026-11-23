package org.example.chesshost.chess

import org.example.chesshost.ui.i18n.trNow
import kotlin.math.abs
import kotlin.random.Random

/**
 * Ein Regelwerk: alles, was ein Schachspiel ausmacht, hängt hier dran.
 *
 * Standard-Schach und Schach960 unterscheiden sich nur in der Rochade und der
 * FEN-Schreibweise; weitere Modi (etwa Räuberschach) bringen eine eigene
 * Implementierung mit, ohne dass Brett, Oberfläche oder Turnierlogik davon
 * wissen müssen.
 */
interface RuleSet {

    /** Stabile ID, z. B. "standard" oder "chess960". */
    val id: String

    /** Anzeigename der Oberfläche. */
    val name: String

    /** UCI-Name der Variante für Engines (Stockfish: `UCI_Chess960`). */
    val engineVariant: String

    /** Rochaderechte als Dateibuchstaben (Shredder-FEN) statt `KQkq`. */
    val shredderFen: Boolean

    /** PGN-Tag `Variant`; null bedeutet Standard und wird weggelassen. */
    val pgnVariant: String? get() = null

    /** Alle legalen Züge der Seite am Zug. */
    fun legalMoves(board: Board): List<Move>

    fun isInCheck(board: Board, color: Color): Boolean

    /** Partieende nach dem Zug, sonst null. */
    fun endState(board: Board, positionKeys: List<String>): GameEnd?

    fun san(board: Board, move: Move, legalMoves: List<Move>): String

    /** Schreibweise, die die Engine für diesen Zug erwartet. */
    fun toEngineMove(move: Move): String

    fun parseEngineMove(board: Board, uci: String): Move? {
        val legal = legalMoves(board)
        legal.firstOrNull { toEngineMove(it) == uci }?.let { return it }
        return legal.firstOrNull { it.uci() == uci }
    }

    /** Rochadefeld der FEN-Zeile. */
    fun castlingField(board: Board): String

    /** Prüft eine zusammengestellte Stellung; leere Liste = spielbar. */
    fun validate(board: Board): List<String>

    /** Zufällige Startstellung (nur Schach960), sonst null. */
    fun randomStartFen(random: Random): String? = null
}

/**
 * Standard-Schach und Schach960. Die Zugerzeugung ist für beide identisch; der
 * Unterschied steckt in den Turmfeldern (siehe [Castling]) und in der
 * FEN-Schreibweise.
 */
open class StandardRules(
    override val id: String = ID,
    override val name: String = "Standard",
    override val engineVariant: String = "standard",
    override val shredderFen: Boolean = false,
    override val pgnVariant: String? = null,
) : RuleSet {

    override fun legalMoves(board: Board): List<Move> {
        val pseudo = ArrayList<Move>(48)
        generatePseudoMoves(board, pseudo)
        val mover = board.sideToMove
        val legal = ArrayList<Move>(pseudo.size)
        for (move in pseudo) {
            val undo = board.makeMove(move)
            val ok = !isInCheck(board, mover)
            board.undoMove(undo)
            if (ok) legal.add(move)
        }
        return legal
    }

    override fun isInCheck(board: Board, color: Color): Boolean {
        val king = board.kingSquare(color)
        if (king < 0) return false
        return board.isAttacked(king, color.opposite)
    }

    override fun endState(board: Board, positionKeys: List<String>): GameEnd? {
        val side = board.sideToMove
        if (legalMoves(board).isEmpty()) {
            return if (isInCheck(board, side)) {
                GameEnd(
                    result = if (side == Color.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS,
                    reason = EndReason.CHECKMATE,
                )
            } else {
                GameEnd(GameResult.DRAW, EndReason.STALEMATE)
            }
        }
        if (board.halfmoveClock >= 100) return GameEnd(GameResult.DRAW, EndReason.FIFTY_MOVES)
        if (positionKeys.count { it == board.positionKey() } >= 3) {
            return GameEnd(GameResult.DRAW, EndReason.THREEFOLD_REPETITION)
        }
        if (hasInsufficientMaterial(board)) return GameEnd(GameResult.DRAW, EndReason.INSUFFICIENT_MATERIAL)
        return null
    }

    override fun san(board: Board, move: Move, legalMoves: List<Move>): String {
        val mover = board.pieceAt(move.from) ?: return ""
        if (move.isCastle) {
            val base = if (fileOf(move.to) == 6) "O-O" else "O-O-O"
            return base + checkSuffix(board, move)
        }

        val sb = StringBuilder()
        val isCapture = move.enPassant || board.pieceAt(move.to) != null

        if (mover.type == PieceType.PAWN) {
            if (isCapture) sb.append('a' + fileOf(move.from)).append('x')
            sb.append(squareName(move.to))
            if (move.promotion != null) sb.append('=').append(move.promotion.letter)
        } else {
            sb.append(mover.type.letter)
            sb.append(disambiguation(board, move, mover, legalMoves))
            if (isCapture) sb.append('x')
            sb.append(squareName(move.to))
        }

        sb.append(checkSuffix(board, move))
        return sb.toString()
    }

    override fun toEngineMove(move: Move): String {
        // Schach960: Die Engine erwartet bei der Rochade den Turm als Zielfeld.
        if (move.isCastle && engineVariant == "chess960") {
            return squareName(move.from) + squareName(move.castleRook)
        }
        return move.uci()
    }

    override fun castlingField(board: Board): String {
        val castling = board.castling
        val sb = StringBuilder()
        for (color in Color.entries) {
            for (kingSide in booleanArrayOf(true, false)) {
                if (!castling.has(color, kingSide)) continue
                if (shredderFen) {
                    val letter = 'a' + fileOf(castling.rookFrom(color, kingSide))
                    sb.append(if (color == Color.WHITE) letter.uppercaseChar() else letter)
                } else {
                    val letter = when {
                        color == Color.WHITE && kingSide -> 'K'
                        color == Color.WHITE -> 'Q'
                        kingSide -> 'k'
                        else -> 'q'
                    }
                    sb.append(letter)
                }
            }
        }
        return if (sb.isEmpty()) "-" else sb.toString()
    }

    override fun validate(board: Board): List<String> {
        val problems = mutableListOf<String>()
        for (color in Color.entries) {
            val label = if (color == Color.WHITE) "Weiß" else "Schwarz"
            val king = board.kingSquare(color)
            when {
                king < 0 -> problems.add("$label hat keinen König.")
                else -> {
                    val other = board.kingSquare(color.opposite)
                    if (other >= 0 && adjacent(king, other)) {
                        problems.add("Die Könige stehen nebeneinander.")
                    }
                }
            }
        }

        if (problems.isEmpty()) {
            val idle = board.sideToMove.opposite
            if (isInCheck(board, idle)) {
                problems.add("Die Seite, die nicht zieht, steht im Schach.")
            }
        }

        for (square in 0 until 64) {
            val piece = board.pieceAt(square) ?: continue
            if (piece.type == PieceType.PAWN && (rankOf(square) == 0 || rankOf(square) == 7)) {
                problems.add(trNow("Bauer auf der Grund- oder letzten Reihe (%s).", squareName(square)))
                break
            }
        }

        val castling = board.castling
        for (color in Color.entries) {
            for (kingSide in booleanArrayOf(true, false)) {
                if (!castling.has(color, kingSide)) continue
                val king = castling.kingFrom(color)
                val rook = castling.rookFrom(color, kingSide)
                val rookPiece = if (rook in 0..63) board.pieceAt(rook) else null
                when {
                    king < 0 || board.kingSquare(color) < 0 ->
                        problems.add("Rochaderecht ohne König.")
                    rookPiece == null || rookPiece.type != PieceType.ROOK || rookPiece.color != color ->
                        problems.add(trNow("Rochaderecht ohne Turm auf %s.", squareName(rook)))
                    king >= 0 && board.kingSquare(color) != king ->
                        problems.add("Rochaderecht, obwohl der König nicht auf seinem Startfeld steht.")
                }
            }
        }

        val ep = board.enPassantSquare
        if (ep >= 0) {
            val rank = rankOf(ep)
            if (rank != 2 && rank != 5) {
                problems.add("En-passant-Feld liegt auf einer ungültigen Reihe.")
            }
            if (board.pieceAt(ep) != null) {
                problems.add("En-passant-Feld ist besetzt.")
            }
        }

        return problems
    }

    private fun adjacent(first: Int, second: Int): Boolean =
        abs(fileOf(first) - fileOf(second)) <= 1 && abs(rankOf(first) - rankOf(second)) <= 1

    private fun checkSuffix(board: Board, move: Move): String {
        val test = board.copy()
        test.makeMove(move)
        if (!isInCheck(test, test.sideToMove)) return ""
        return if (legalMoves(test).isEmpty()) "#" else "+"
    }

    private fun disambiguation(board: Board, move: Move, mover: Piece, legalMoves: List<Move>): String {
        val rivals = legalMoves.filter {
            it.to == move.to && it.from != move.from && board.pieceAt(it.from) == mover
        }
        if (rivals.isEmpty()) return ""

        val sameFile = rivals.any { fileOf(it.from) == fileOf(move.from) }
        val sameRank = rivals.any { rankOf(it.from) == rankOf(move.from) }
        return when {
            !sameFile -> ('a' + fileOf(move.from)).toString()
            !sameRank -> (rankOf(move.from) + 1).toString()
            else -> squareName(move.from)
        }
    }

    private fun hasInsufficientMaterial(board: Board): Boolean {
        val pieces = board.snapshot().filterNotNull()
        if (pieces.any { it.type == PieceType.PAWN || it.type == PieceType.ROOK || it.type == PieceType.QUEEN }) {
            return false
        }
        val minors = pieces.filter { it.type == PieceType.KNIGHT || it.type == PieceType.BISHOP }
        if (minors.size <= 1) return true

        if (minors.all { it.type == PieceType.BISHOP }) {
            val bishopSquares = (0 until 64).filter { i -> board.pieceAt(i)?.type == PieceType.BISHOP }
            val squareColors = bishopSquares.map { (fileOf(it) + rankOf(it)) % 2 }.toSet()
            if (squareColors.size == 1) return true
        }
        return false
    }

    private fun generatePseudoMoves(board: Board, moves: MutableList<Move>) {
        val us = board.sideToMove
        for (from in 0 until 64) {
            val piece = board.pieceAt(from) ?: continue
            if (piece.color != us) continue
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(board, from, us, moves)
                PieceType.KNIGHT -> addStepMoves(board, from, us, KNIGHT_OFFSETS, moves)
                PieceType.KING -> addStepMoves(board, from, us, KING_OFFSETS, moves)
                PieceType.BISHOP -> addSlidingMoves(board, from, us, BISHOP_DIRECTIONS, moves)
                PieceType.ROOK -> addSlidingMoves(board, from, us, ROOK_DIRECTIONS, moves)
                PieceType.QUEEN -> {
                    addSlidingMoves(board, from, us, BISHOP_DIRECTIONS, moves)
                    addSlidingMoves(board, from, us, ROOK_DIRECTIONS, moves)
                }
            }
        }
        generateCastles(board, moves)
    }

    private fun addStepMoves(
        board: Board,
        from: Int,
        us: Color,
        offsets: List<Pair<Int, Int>>,
        moves: MutableList<Move>,
    ) {
        val f = fileOf(from)
        val r = rankOf(from)
        for ((df, dr) in offsets) {
            val nf = f + df
            val nr = r + dr
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = nr * 8 + nf
            val target = board.pieceAt(to)
            if (target == null || target.color != us) moves.add(Move(from, to))
        }
    }

    private fun addSlidingMoves(
        board: Board,
        from: Int,
        us: Color,
        directions: List<Pair<Int, Int>>,
        moves: MutableList<Move>,
    ) {
        val f = fileOf(from)
        val r = rankOf(from)
        for ((df, dr) in directions) {
            var nf = f + df
            var nr = r + dr
            while (nf in 0..7 && nr in 0..7) {
                val to = nr * 8 + nf
                val target = board.pieceAt(to)
                if (target == null) {
                    moves.add(Move(from, to))
                } else {
                    if (target.color != us) moves.add(Move(from, to))
                    break
                }
                nf += df
                nr += dr
            }
        }
    }

    private fun generatePawnMoves(board: Board, from: Int, us: Color, moves: MutableList<Move>) {
        val forward = if (us == Color.WHITE) 1 else -1
        val startRank = if (us == Color.WHITE) 1 else 6
        val promotionRank = if (us == Color.WHITE) 7 else 0
        val f = fileOf(from)
        val r = rankOf(from)

        val oneRank = r + forward
        if (oneRank in 0..7) {
            val one = oneRank * 8 + f
            if (board.pieceAt(one) == null) {
                if (oneRank == promotionRank) {
                    addPromotions(from, one, moves)
                } else {
                    moves.add(Move(from, one))
                    if (r == startRank) {
                        val two = (r + 2 * forward) * 8 + f
                        if (board.pieceAt(two) == null) moves.add(Move(from, two))
                    }
                }
            }

            for (df in intArrayOf(-1, 1)) {
                val nf = f + df
                if (nf !in 0..7) continue
                val to = oneRank * 8 + nf
                val target = board.pieceAt(to)
                if (target != null && target.color != us) {
                    if (oneRank == promotionRank) addPromotions(from, to, moves) else moves.add(Move(from, to))
                } else if (to == board.enPassantSquare && target == null) {
                    val victimSquare = r * 8 + nf
                    val victim = board.pieceAt(victimSquare)
                    if (victim != null && victim.type == PieceType.PAWN && victim.color != us) {
                        moves.add(Move(from, to, enPassant = true))
                    }
                }
            }
        }
    }

    private fun addPromotions(from: Int, to: Int, moves: MutableList<Move>) {
        moves.add(Move(from, to, PieceType.QUEEN))
        moves.add(Move(from, to, PieceType.ROOK))
        moves.add(Move(from, to, PieceType.BISHOP))
        moves.add(Move(from, to, PieceType.KNIGHT))
    }

    /**
     * Rochade nach Standard- und Schach960-Regeln. Entscheidend sind die
     * gespeicherten Turmfelder: Bei Schach960 dürfen König und Turm beliebig
     * stehen, die Zielfelder bleiben g1/c1 bzw. f1/d1.
     */
    private fun generateCastles(board: Board, moves: MutableList<Move>) {
        val us = board.sideToMove
        val rank = if (us == Color.WHITE) 0 else 7
        val castling = board.castling

        for (kingSide in booleanArrayOf(true, false)) {
            if (!castling.has(us, kingSide)) continue
            val kingFrom = castling.kingFrom(us)
            val rookFrom = castling.rookFrom(us, kingSide)
            if (kingFrom !in 0..63 || rookFrom !in 0..63) continue
            if (rankOf(kingFrom) != rank || rankOf(rookFrom) != rank) continue

            val king = board.pieceAt(kingFrom)
            val rook = board.pieceAt(rookFrom)
            if (king == null || king.type != PieceType.KING || king.color != us) continue
            if (rook == null || rook.type != PieceType.ROOK || rook.color != us) continue

            val kingTo = rank * 8 + if (kingSide) 6 else 2
            val rookTo = rank * 8 + if (kingSide) 5 else 3

            // Zwischen König und Turm muss alles frei sein.
            var clear = true
            for (square in minOf(kingFrom, rookFrom)..maxOf(kingFrom, rookFrom)) {
                if (square == kingFrom || square == rookFrom) continue
                if (board.pieceAt(square) != null) {
                    clear = false
                    break
                }
            }
            if (!clear) continue

            // Zielfelder müssen frei sein (oder von den beteiligten Figuren belegt).
            var targetsFree = true
            for (square in intArrayOf(kingTo, rookTo)) {
                if (square == kingFrom || square == rookFrom) continue
                if (board.pieceAt(square) != null) {
                    targetsFree = false
                    break
                }
            }
            if (!targetsFree) continue

            // Der König darf nicht im Schach stehen und kein bedrohtes Feld betreten.
            val step = if (kingTo >= kingFrom) 1 else -1
            var square = kingFrom
            var attacked = false
            while (true) {
                if (board.isAttacked(square, us.opposite)) {
                    attacked = true
                    break
                }
                if (square == kingTo) break
                square += step
            }
            if (attacked) continue

            moves.add(Move(kingFrom, kingTo, castleRook = rookFrom))
        }
    }

    companion object {
        const val ID = "standard"

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
 * Schach960: gleiche Regeln, aber die Grundstellung ist zufällig und die
 * Rochade läuft über die tatsächlichen Turmfelder. In FEN wird deshalb die
 * Shredder-Schreibweise (`HAha`) verwendet.
 */
object Chess960Rules : StandardRules(
    id = "chess960",
    name = "Schach960",
    engineVariant = "chess960",
    shredderFen = true,
    pgnVariant = "Chess960",
) {

    override fun randomStartFen(random: Random): String {
        val back = arrayOfNulls<PieceType>(8)

        // Läufer auf verschiedenfarbigen Feldern.
        back[listOf(1, 3, 5, 7).random(random)] = PieceType.BISHOP
        back[listOf(0, 2, 4, 6).random(random)] = PieceType.BISHOP

        // Dame, dann zwei Springer, Rest R K R.
        back[(0..7).filter { back[it] == null }.random(random)] = PieceType.QUEEN
        val forKnights = (0..7).filter { back[it] == null }.shuffled(random)
        back[forKnights[0]] = PieceType.KNIGHT
        back[forKnights[1]] = PieceType.KNIGHT
        val rest = (0..7).filter { back[it] == null }
        back[rest[0]] = PieceType.ROOK
        back[rest[1]] = PieceType.KING
        back[rest[2]] = PieceType.ROOK

        val white = back.joinToString("") { it!!.letter.toString() }
        val black = back.joinToString("") { it!!.letter.lowercaseChar().toString() }
        val kingFile = rest[1]
        val kingRook = 'A' + rest[2]
        val queenRook = 'A' + rest[0]
        val castling = "" +
            queenRook + kingRook +                       // Weiß: Damenflügel zuerst
            queenRook.lowercaseChar() + kingRook.lowercaseChar()
        require(kingFile in 1..6)
        return "$black/pppppppp/8/8/8/8/PPPPPPPP/$white w $castling - 0 1"
    }
}

/** Zugriff auf die verfügbaren Regelwerke. */
object Rules {

    val standard: RuleSet = StandardRules()

    val chess960: RuleSet = Chess960Rules

    val all: List<RuleSet> = listOf(standard, chess960)

    fun byId(id: String?): RuleSet = all.firstOrNull { it.id == id } ?: standard
}
