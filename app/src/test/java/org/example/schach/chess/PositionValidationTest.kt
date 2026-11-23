package org.example.schach.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüfungen des Stellungseditors: was darf gespeichert und gespielt werden? */
class PositionValidationTest {

    @Test
    fun `leeres brett wird abgelehnt`() {
        val problems = Rules.standard.validate(Board.empty())

        assertTrue(problems.any { it.contains("keinen König") })
    }

    @Test
    fun `grundstellung ist fehlerfrei`() {
        assertEquals(emptyList<String>(), Rules.standard.validate(Board.initial()))
        assertEquals(emptyList<String>(), Chess960Rules.validate(Board.initial(Chess960Rules)))
    }

    @Test
    fun `koenige duerfen nicht nebeneinander stehen`() {
        val board = Board.fromFen("8/8/8/8/8/8/8/Kk6 w - - 0 1")

        assertTrue(Rules.standard.validate(board).any { it.contains("nebeneinander") })
    }

    @Test
    fun `die seite die nicht zieht darf nicht im schach stehen`() {
        val board = Board.fromFen("k7/8/8/8/8/8/8/R3K3 w - - 0 1")

        assertTrue(Rules.standard.validate(board).any { it.contains("nicht zieht") })
    }

    @Test
    fun `bauern auf der grundreihe sind unzulaessig`() {
        val board = Board.fromFen("P3k3/8/8/8/8/8/8/4K3 w - - 0 1")

        assertTrue(Rules.standard.validate(board).any { it.contains("Bauer") })
    }

    @Test
    fun `rochaderecht ohne turm wird beim einlesen verworfen`() {
        // FEN behauptet K, auf h1 steht aber kein Turm.
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4K3 w K - 0 1")

        assertTrue(!board.castling.has(Color.WHITE, kingSide = true))
        assertEquals(emptyList<String>(), Rules.standard.validate(board))
        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", board.fen())
    }

    @Test
    fun `editorstellung mit koenig und turing ist spielbar`() {
        val board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")

        assertEquals(emptyList<String>(), Rules.standard.validate(board))
        assertTrue(board.legalMoves().any { it.isCastle })
    }

    @Test
    fun `stellung laesst sich feldweise bauen und als fen speichern`() {
        val squares = arrayOfNulls<Piece>(64)
        squares[4] = Piece(PieceType.KING, Color.WHITE)
        squares[60] = Piece(PieceType.KING, Color.BLACK)
        squares[0] = Piece(PieceType.ROOK, Color.WHITE)

        val board = Board.fromPieces(squares, Color.WHITE, Castling.none())

        assertEquals(emptyList<String>(), Rules.standard.validate(board))
        assertEquals("4k3/8/8/8/8/8/8/R3K3 w - - 0 1", board.fen())
        assertEquals(board.fen(), Board.fromFen(board.fen()).fen())
    }
}
