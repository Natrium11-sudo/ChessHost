package org.example.schach.engine

import org.example.schach.chess.Board
import org.example.schach.chess.Color
import org.example.schach.chess.Move
import org.example.schach.chess.Piece
import org.example.schach.chess.PieceType
import org.example.schach.chess.Rules
import org.example.schach.chess.rankOf
import org.example.schach.chess.fileOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.random.Random

/**
 * Interne Fallback-Engine (Negamax mit Alpha-Beta). Sie springt nur ein, wenn
 * Stockfish auf dem Gerät nicht gestartet werden kann, damit die App in jedem
 * Fall spielbar bleibt.
 */
class SimpleEngine(private val random: Random = Random.Default) : ChessEngine {

    override val displayName: String = "Interne Engine"

    override suspend fun start(): Boolean = true

    override suspend fun bestMove(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): String? = withContext(Dispatchers.Default) {
        val board = Board.fromFen(currentFen, Rules.byId(config.variant))
        val legal = board.legalMoves()
        if (legal.isEmpty()) return@withContext null

        val depth = when {
            config.skillLevel <= 2 -> 0
            config.skillLevel <= 5 -> 1
            config.skillLevel <= 10 -> 2
            config.skillLevel <= 15 -> 3
            else -> 3
        }
        if (depth == 0) return@withContext board.rules.toEngineMove(legal.random(random))

        val me = board.sideToMove
        var bestScore = Int.MIN_VALUE
        var bestMove = legal.first()

        for (move in legal) {
            coroutineContext.ensureActive()
            val undo = board.makeMove(move)
            val score = -negamax(board, depth - 1, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1, me.opposite, 1)
            board.undoMove(undo)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        board.rules.toEngineMove(bestMove)
    }

    private suspend fun negamax(
        board: Board,
        depth: Int,
        alphaIn: Int,
        beta: Int,
        side: Color,
        ply: Int,
    ): Int {
        if (depth == 0) return evaluate(board, side)

        var alpha = alphaIn
        val moves = board.legalMoves()
        if (moves.isEmpty()) {
            return if (board.isInCheck(side)) -MATE_SCORE + ply else 0
        }

        for (move in moves) {
            coroutineContext.ensureActive()
            val undo = board.makeMove(move)
            val score = -negamax(board, depth - 1, -beta, -alpha, side.opposite, ply + 1)
            board.undoMove(undo)
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return alpha
    }

    private fun evaluate(board: Board, side: Color): Int {
        var score = 0
        for (square in 0 until 64) {
            val piece = board.pieceAt(square) ?: continue
            val value = PIECE_VALUES.getValue(piece.type) + positionalBonus(piece, square)
            score += if (piece.color == side) value else -value
        }
        return score
    }

    private fun positionalBonus(piece: Piece, square: Int): Int {
        val file = fileOf(square)
        val rank = rankOf(square)
        val advanced = if (piece.color == Color.WHITE) rank else 7 - rank
        val centerDistance = abs(file - 3) + abs(rank - 3)
        return when (piece.type) {
            PieceType.PAWN -> advanced * 6
            PieceType.KNIGHT, PieceType.BISHOP -> 14 - centerDistance * 2
            PieceType.ROOK -> advanced
            PieceType.QUEEN -> 4 - centerDistance
            PieceType.KING -> 0
        }
    }

    override suspend fun stopSearch() = Unit

    override fun close() = Unit

    companion object {
        private const val MATE_SCORE = 100_000

        private val PIECE_VALUES = mapOf(
            PieceType.PAWN to 100,
            PieceType.KNIGHT to 320,
            PieceType.BISHOP to 330,
            PieceType.ROOK to 500,
            PieceType.QUEEN to 900,
            PieceType.KING to 20_000,
        )
    }
}
