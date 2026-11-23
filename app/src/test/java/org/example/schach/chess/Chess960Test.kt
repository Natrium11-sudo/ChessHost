package org.example.schach.chess

import org.example.schach.engine.EngineConfig
import org.example.schach.engine.SimpleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Schach960: Rochade über Turmfelder, Shredder-FEN und Engine-Schreibweise. */
class Chess960Test {

    /** Freies Brett: König b1, Türme a1 und h1 – beide Rochaden sind möglich. */
    private val open = "4k3/8/8/8/8/8/8/RK5R w HA - 0 1"

    /** Echte 960-Grundstellung: König g1, Türme f1 und h1 (lange Rochade versperrt). */
    private val packed = "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1"

    @Test
    fun `fen mit turmdateien wird gelesen und wieder geschrieben`() {
        val board = Board.fromFen(packed, Chess960Rules)

        assertEquals(packed, board.fen())
        assertEquals(6, board.castling.kingFrom(Color.WHITE))
        assertEquals(7, board.castling.rookFrom(Color.WHITE, kingSide = true))
        assertEquals(5, board.castling.rookFrom(Color.WHITE, kingSide = false))
        assertTrue(board.castling.has(Color.BLACK, kingSide = true))
    }

    @Test
    fun `kurze rochade zieht koenig und turm auf die zielfelder`() {
        val board = Board.fromFen(open, Chess960Rules)
        val kingSide = board.legalMoves().firstOrNull { it.isCastle && it.to == 6 }

        assertNotNull("kurze Rochade fehlt", kingSide)
        assertEquals(1, kingSide!!.from)
        assertEquals(7, kingSide.castleRook)

        board.makeMove(kingSide)
        assertEquals(Piece(PieceType.KING, Color.WHITE), board.pieceAt(6))
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), board.pieceAt(5))
        assertNull(board.pieceAt(7))
        assertTrue(!board.castling.has(Color.WHITE, kingSide = true))
        assertTrue(!board.castling.has(Color.WHITE, kingSide = false))
    }

    @Test
    fun `lange rochade funktioniert genauso`() {
        val board = Board.fromFen(open, Chess960Rules)
        val queenSide = board.legalMoves().firstOrNull { it.isCastle && it.to == 2 }

        assertNotNull(queenSide)
        assertEquals(0, queenSide!!.castleRook)

        board.makeMove(queenSide)
        assertEquals(Piece(PieceType.KING, Color.WHITE), board.pieceAt(2))
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), board.pieceAt(3))
    }

    @Test
    fun `lange rochade ist versperrt wenn figuren dazwischen stehen`() {
        val board = Board.fromFen(packed, Chess960Rules)

        assertTrue(board.legalMoves().none { it.isCastle && it.castleRook == 5 })
    }

    @Test
    fun `rochade laesst sich exakt zuruecknehmen`() {
        val board = Board.fromFen(open, Chess960Rules)
        val fenBefore = board.fen()
        val move = board.legalMoves().first { it.isCastle }

        val undo = board.makeMove(move)
        board.undoMove(undo)

        assertEquals(fenBefore, board.fen())
    }

    @Test
    fun `engine bekommt bei der rochade den turm als zielfeld`() {
        val board = Board.fromFen(open, Chess960Rules)
        val move = board.legalMoves().first { it.isCastle && it.to == 6 }

        assertEquals("b1h1", Chess960Rules.toEngineMove(move))
        assertEquals(move, Chess960Rules.parseEngineMove(board, "b1h1"))
    }

    @Test
    fun `standard meldet die rochade als koenigszug`() {
        val board = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val move = board.legalMoves().first { it.isCastle && it.to == 6 }

        assertEquals("e1g1", Rules.standard.toEngineMove(move))
        assertEquals(move, Rules.standard.parseEngineMove(board, "e1g1"))
    }

    @Test
    fun `koenig auf g1 bleibt bei der kurzen rochade stehen`() {
        // Türme e1 und h1, König g1: kurze Rochade setzt nur den Turm um.
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4R1KR w EH - 0 1", Chess960Rules)
        val castles = board.legalMoves().filter { it.isCastle }

        assertEquals(2, castles.size)
        val kingSide = castles.first { it.castleRook == 7 }
        assertEquals(6, kingSide.from)
        assertEquals(6, kingSide.to)

        board.makeMove(kingSide)
        assertEquals(Piece(PieceType.KING, Color.WHITE), board.pieceAt(6))
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), board.pieceAt(5))
    }

    @Test
    fun `rochade ist gesperrt wenn der koenig ein bedrohtes feld betreten muss`() {
        // Schwarzer Turm g8 deckt g1; König f1 will über g1 ziehen.
        val board = Board.fromFen("6rk/8/8/8/8/8/8/5K1R w H - 0 1", Chess960Rules)

        assertTrue(board.legalMoves().none { it.isCastle })
        // Ohne den Turm auf g8 ist dieselbe Rochade erlaubt.
        val free = Board.fromFen("7k/8/8/8/8/8/8/5K1R w H - 0 1", Chess960Rules)
        assertTrue(free.legalMoves().any { it.isCastle })
    }

    @Test
    fun `zufallsstellungen sind gueltig und haben die richtige grundordnung`() {
        repeat(50) { seed ->
            val fen = Chess960Rules.randomStartFen(Random(seed))
            val board = Board.fromFen(fen, Chess960Rules)

            assertTrue(
                "Stellung $seed ungültig: ${Chess960Rules.validate(board)}",
                Chess960Rules.validate(board).isEmpty(),
            )
            assertTrue("Stellung $seed: keine Rochaderechte", board.castling.has(Color.WHITE, true))
            assertTrue("Stellung $seed: keine Rochaderechte", board.castling.has(Color.WHITE, false))
            assertTrue("Stellung $seed: keine Züge", board.legalMoves().isNotEmpty())

            val king = board.kingSquare(Color.WHITE)
            val leftRook = board.castling.rookFrom(Color.WHITE, false)
            val rightRook = board.castling.rookFrom(Color.WHITE, true)
            assertTrue("Stellung $seed: König nicht zwischen den Türmen", leftRook < king && king < rightRook)

            val bishops = (0..7).filter { file -> board.pieceAt(file)?.type == PieceType.BISHOP }
            assertEquals(2, bishops.size)
            assertEquals(1, (bishops[0] + bishops[1]) % 2)
        }
    }

    @Test
    fun `x-fen mit KQkq wird ebenfalls verstanden`() {
        val board = Board.fromFen("bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1", Chess960Rules)

        assertEquals(7, board.castling.rookFrom(Color.WHITE, kingSide = true))
        assertEquals(5, board.castling.rookFrom(Color.WHITE, kingSide = false))
        assertEquals(7, board.castling.rookFrom(Color.BLACK, kingSide = false) / 8)
    }

    @Test
    fun `pgn bekommt den varianten-tag`() {
        val game = ChessGame(open, Chess960Rules)
        game.makeMove(game.legalMoves().first { it.isCastle })

        val pgn = game.toPgn("A", "B")

        assertTrue(pgn.contains("[Variant \"Chess960\"]"))
        assertTrue(pgn.contains("O-O"))
        assertTrue(pgn.contains("[SetUp \"1\"]"))
    }

    /**
     * Eine Engine-Partie in Schach960: jeder Zug geht als UCI-String raus und
     * muss wieder als legaler Zug zurückkommen – inklusive der Rochade, die die
     * Engine als König-auf-Turm meldet.
     */
    @Test
    fun `engine-partie in schach960 laeuft ohne fehler`() = runBlocking {
        repeat(3) { seed ->
            val fen = Chess960Rules.randomStartFen(Random(seed))
            val game = ChessGame(fen, Chess960Rules)
            val engine = SimpleEngine(Random(seed))
            var plies = 0

            while (game.end == null && plies < 60) {
                val uci = engine.bestMove(
                    startFen = fen,
                    uciHistory = game.uciHistory(),
                    currentFen = game.board.fen(),
                    config = EngineConfig(skillLevel = 4, moveTimeMs = 20, variant = Chess960Rules.id),
                )
                assertNotNull("Stellung $seed: Engine lieferte keinen Zug", uci)
                val move = game.parseEngineMove(uci!!)
                assertNotNull("Stellung $seed: $uci ist kein legaler Zug", move)
                game.makeMove(move!!)
                plies++
            }

            assertEquals(plies, game.sanMoves.size)
        }
    }
}
