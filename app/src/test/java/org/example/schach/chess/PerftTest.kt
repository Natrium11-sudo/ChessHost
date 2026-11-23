package org.example.schach.chess

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Perft-Tests: vergleicht die Zahl der Blattknoten mit den bekannten Referenzwerten
 * der Schachprogrammier-Community. Das prüft Zuggenerierung, Rochade, En passant,
 * Umwandlungen und Schachregeln sehr gründlich.
 */
class PerftTest {

    private fun perft(board: Board, depth: Int): Long {
        val moves = board.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var nodes = 0L
        for (move in moves) {
            val undo = board.makeMove(move)
            nodes += perft(board, depth - 1)
            board.undoMove(undo)
        }
        return nodes
    }

    private fun check(fen: String, expected: List<Long>) {
        val board = Board.fromFen(fen)
        for ((index, value) in expected.withIndex()) {
            assertEquals("FEN $fen Tiefe ${index + 1}", value, perft(board, index + 1))
        }
    }

    @Test
    fun startPosition() {
        check(Board.START_FEN, listOf(20, 400, 8902, 197281))
    }

    @Test
    fun kiwipete() {
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", listOf(48, 2039, 97862))
    }

    @Test
    fun position3EnPassantAndPins() {
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", listOf(14, 191, 2812, 43238))
    }

    @Test
    fun position4Promotions() {
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", listOf(6, 264, 9467))
    }

    @Test
    fun position5() {
        check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", listOf(44, 1486, 62379))
    }

    @Test
    fun position6() {
        check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", listOf(46, 2079, 89890))
    }
}
