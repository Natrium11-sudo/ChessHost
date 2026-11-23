package org.example.chesshost.engine.llm

import org.example.chesshost.chess.Board
import org.example.chesshost.chess.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmEngineTest {

    private val board = Board.fromFen(Board.START_FEN, Rules.standard)

    @Test
    fun `UCI-Zug wird erkannt`() {
        val move = extractMove("e2e4", board, Rules.standard)
        assertEquals("e2e4", move?.uci())
    }

    @Test
    fun `UCI-Zug in Fliesstext und Backticks wird erkannt`() {
        val move = extractMove("I would play `g1f3` here.", board, Rules.standard)
        assertEquals("g1f3", move?.uci())
    }

    @Test
    fun `SAN-Zug wird erkannt`() {
        val move = extractMove("Beste Antwort: Nf3.", board, Rules.standard)
        assertEquals("g1f3", move?.uci())
    }

    @Test
    fun `illegaler Zug liefert null`() {
        assertNull(extractMove("e2e5", board, Rules.standard))
    }

    @Test
    fun `Antwort ohne Zug liefert null`() {
        assertNull(extractMove("Ich kann gerade keinen Zug empfehlen.", board, Rules.standard))
    }
}
