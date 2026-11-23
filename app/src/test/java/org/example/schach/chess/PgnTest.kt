package org.example.schach.chess

import org.example.schach.engine.EngineEval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgnTest {

    private val spanish = """
        [Event "Test"]
        [White "Alpha"]
        [Black "Beta"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0
    """.trimIndent()

    @Test
    fun `kopfzeilen und zuege werden gelesen`() {
        val tags = Pgn.tags(spanish)
        assertEquals("Alpha", tags["White"])
        assertEquals("Beta", tags["Black"])
        assertEquals(10, Pgn.moves(spanish).size)
    }

    @Test
    fun `partie wird bis zum ende nachgespielt`() {
        val replay = Pgn.replay(spanish)
        assertEquals(10, replay.moveCount)
        assertEquals(11, replay.steps.size)
        assertEquals("Be7", replay.steps.last().san)
        assertEquals("1-0", replay.result)
        // Nach der Rochade steht der König auf g1.
        assertTrue(replay.steps.last().fen.startsWith("r1bqk2r"))
    }

    @Test
    fun `kommentare und varianten stoeren nicht`() {
        val pgn = "1. e4 {Königsbauer} e5 (1... c5 2. Nf3) 2. Nf3 Nc6 3. Bb5"
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5"), Pgn.moves(pgn))
        assertEquals(5, Pgn.replay(pgn).moveCount)
    }

    @Test
    fun `rochade in null-schreibweise wird verstanden`() {
        val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. 0-0 d6"
        assertEquals(8, Pgn.replay(pgn).moveCount)
    }

    @Test
    fun `unbekannter zug beendet die auswertung`() {
        val replay = Pgn.replay("1. e4 e5 2. Zz9")
        assertEquals(2, replay.moveCount)
    }

    @Test
    fun `stellung mit fen-kopfzeile startet dort`() {
        val pgn = """
            [SetUp "1"]
            [FEN "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"]

            1. e4 Kd7
        """.trimIndent()
        val replay = Pgn.replay(pgn)
        assertEquals(2, replay.moveCount)
        assertFalse(replay.steps.first().fen.startsWith("rnbq"))
    }

    @Test
    fun `bewertung wird als text und balkenanteil ausgegeben`() {
        val eval = EngineEval(centipawns = 120, mateIn = null, bestMoveUci = "e2e4", pv = emptyList())
        assertEquals("+1.20", eval.label())
        assertTrue(eval.whiteShare > 0.5f)

        val mate = EngineEval(centipawns = null, mateIn = 3, bestMoveUci = null, pv = emptyList())
        assertEquals("Matt in 3", mate.label())
        assertTrue(mate.whiteShare > 0.9f)
    }
}
