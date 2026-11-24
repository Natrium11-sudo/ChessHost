package org.example.chesshost.engine.llm

import org.example.chesshost.chess.Board
import org.example.chesshost.chess.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmEngineTest {

    private val board = Board.fromFen(Board.START_FEN, Rules.standard)
    private val legal = board.legalMoves()

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

    @Test
    fun `MOVE-Nummer waehlt den Zug aus der Liste`() {
        val move = parseChoice("MOVE: 2", board, Rules.standard)
        assertEquals(legal[1], move)
    }

    @Test
    fun `einzelne Nummer ohne Praefix waehlt den Zug aus der Liste`() {
        val move = parseChoice("Ich nehme 12.", board, Rules.standard)
        assertEquals(legal[11], move)
    }

    @Test
    fun `Nummer ausserhalb der Liste liefert null`() {
        assertNull(parseChoice("MOVE: 99", board, Rules.standard))
    }

    @Test
    fun `MOVE-Nummer schlaegt andere Zahlen im Text`() {
        val move = parseChoice("MOVE: 3. Option 7 waere auch legal.", board, Rules.standard)
        assertEquals(legal[2], move)
    }

    @Test
    fun `SAN-Historie wird mit Zugnummern aufgebaut`() {
        val history = sanHistory(Board.START_FEN, listOf("e2e4", "e7e5", "g1f3"), Rules.standard)
        assertEquals(listOf("1.", "e4", "e5", "2.", "Nf3"), history)
    }

    @Test
    fun `unbekannter Zug im Verlauf liefert leere Historie`() {
        assertTrue(sanHistory(Board.START_FEN, listOf("e2e5"), Rules.standard).isEmpty())
    }

    @Test
    fun `Brettdiagramm zeigt Startstellung`() {
        val rows = boardDiagram(board).lines()
        assertEquals("8 r n b q k b n r", rows[0])
        assertEquals("1 R N B Q K B N R", rows[7])
        assertEquals("  a b c d e f g h", rows[8])
    }

    @Test
    fun `Prompt enthaelt Historie und nummerierte Zugliste`() {
        val prompt = buildPositionPrompt(
            board,
            Rules.standard,
            legal,
            listOf("e2e4", "e7e5"),
            Board.START_FEN,
            10,
        )
        assertTrue(prompt.contains("1. e4 e5"))
        assertTrue(prompt.contains(" 1. ${Rules.standard.toEngineMove(legal[0])}"))
        assertTrue(prompt.contains("${legal.size}."))
        assertTrue(prompt.contains("MOVE: 1"))
    }
}
