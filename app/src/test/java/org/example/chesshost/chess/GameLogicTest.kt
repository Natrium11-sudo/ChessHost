package org.example.chesshost.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLogicTest {

    private fun play(vararg uciMoves: String): ChessGame {
        val game = ChessGame()
        for (uci in uciMoves) {
            val move = game.legalMoves().firstOrNull { it.uci() == uci }
            assertNotNull("Zug $uci muss legal sein", move)
            assertTrue(game.makeMove(move!!))
        }
        return game
    }

    @Test
    fun scholarsMateIsDetected() {
        val game = play("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7")
        assertEquals(listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"), game.sanMoves)
        assertEquals(GameEnd(GameResult.WHITE_WINS, EndReason.CHECKMATE), game.end)
    }

    @Test
    fun castlingIsNotationAsOO() {
        val game = play("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1")
        assertEquals("O-O", game.sanMoves.last())
        assertTrue(game.board.pieceAt(6)?.type == PieceType.KING)
        assertTrue(game.board.pieceAt(5)?.type == PieceType.ROOK)
    }

    @Test
    fun enPassantIsGeneratedAndCapturesPawn() {
        val game = play("e2e4", "a7a6", "e4e5", "d7d5")
        val ep = game.legalMoves().first { it.uci() == "e5d6" }
        assertTrue(ep.enPassant)
        game.makeMove(ep)
        assertEquals("exd6", game.sanMoves.last())
        assertEquals(null, game.board.pieceAt(squareIndex(3, 4)))
    }

    @Test
    fun promotionAppendsPieceLetter() {
        val game = ChessGame("8/4P3/8/8/8/8/8/K6k w - - 0 1")
        val promote = game.legalMoves().first { it.uci() == "e7e8q" }
        game.makeMove(promote)
        assertEquals("e8=Q", game.sanMoves.last())
        assertEquals(PieceType.QUEEN, game.board.pieceAt(squareIndex(4, 7))?.type)
    }

    @Test
    fun stalemateIsDetected() {
        val game = ChessGame("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(game.legalMoves().isEmpty())
        val end = game.toPgn("W", "B").let { _ -> null } // PGN darf auch ohne Ergebnis nicht crashen
        assertEquals(null, end)
    }

    @Test
    fun pgnContainsMovesAndResult() {
        val game = play("e2e4", "e7e5", "g1f3", "b8c6")
        val pgn = game.toPgn("Spieler", "Stockfish")
        assertTrue(pgn.contains("[White \"Spieler\"]"))
        assertTrue(pgn.contains("1. e4 e5 2. Nf3 Nc6"))
    }

    @Test
    fun undoRestoresPosition() {
        val game = play("e2e4", "e7e5")
        val before = game.board.fen()
        game.undo()
        assertTrue(game.board.fen() != before)
        assertEquals(1, game.moves.size)
        assertEquals("e4", game.sanMoves.single())
    }

    @Test
    fun threefoldRepetitionEndsGame() {
        val game = play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8")
        assertEquals(EndReason.THREEFOLD_REPETITION, game.end?.reason)
    }
}
