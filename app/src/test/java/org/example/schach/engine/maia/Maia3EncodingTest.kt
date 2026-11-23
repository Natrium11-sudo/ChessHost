package org.example.schach.engine.maia

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.example.schach.chess.Move
import org.example.schach.chess.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.FloatBuffer

class Maia3EncodingTest {

    private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    @Test
    fun `startstellung hat 32 Figuren auf den richtigen Kanaelen`() {
        val tokens = Maia3Encoding.tokens(startFen)

        assertEquals(64 * 12, tokens.size)
        assertEquals(32, tokens.count { it == 1f })
        // Weißer Turm auf a1 (Feld 0), schwarzer König auf e8 (Feld 60).
        assertEquals(1f, tokens[0 * 12 + PieceType.ROOK.ordinal], 0f)
        assertEquals(1f, tokens[60 * 12 + PieceType.KING.ordinal + 6], 0f)
    }

    @Test
    fun `schwarz am zug wird gespiegelt`() {
        val fen = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
        val tokens = Maia3Encoding.tokens(fen)

        // Der schwarze König von e8 steht nach dem Spiegeln als weißer König auf e1.
        assertEquals(1f, tokens[4 * 12 + PieceType.KING.ordinal], 0f)
        // Der weiße König von e1 steht gespiegelt als schwarzer König auf e8.
        assertEquals(1f, tokens[60 * 12 + PieceType.KING.ordinal + 6], 0f)
        assertEquals(32, tokens.count { it == 1f })
    }

    @Test
    fun `zugindizes passen zum Modellvokabular`() {
        // Werte gegen die Referenz (all_moves_maia3.json) geprüft.
        assertEquals(796, Maia3Encoding.moveIndex(Move(12, 28))) // e2e4
        assertEquals(4096 + 72, Maia3Encoding.moveIndex(Move(50, 58, PieceType.QUEEN))) // c7c8q
        assertEquals(4096 + 72 + 3, Maia3Encoding.moveIndex(Move(50, 58, PieceType.KNIGHT))) // c7c8n
    }

    @Test
    fun `spiegeln von FEN und Zuegen ist umkehrbar`() {
        val fen = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 5 12"
        val mirrored = Maia3Encoding.mirrorFen(fen)

        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 5 12", mirrored)
        assertEquals(fen, Maia3Encoding.mirrorFen(mirrored))
        assertEquals("g8f6", Maia3Encoding.mirrorUci("g1f3"))
        assertEquals("g1f3", Maia3Encoding.mirrorUci(Maia3Encoding.mirrorUci("g1f3")))
    }

    @Test
    fun `bester zug folgt den logits und bleibt legal`() {
        val fen = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"
        val logits = FloatArray(Maia3Encoding.MOVE_COUNT) { -10f }
        // e2e4 (12*64+28) künstlich bevorzugen.
        logits[12 * 64 + 28] = 5f

        assertEquals("e2e4", Maia3Encoding.bestMove(fen, logits))
    }

    /**
     * Wertet das echte Netz aus. Läuft nur, wenn ein Modellpfad gesetzt ist:
     * `./gradlew testDebugUnitTest -PmaiaModel=/pfad/maia3-5m.fp16.onnx`.
     *
     * Erwartete Züge stammen aus einer Referenzauswertung mit onnxruntime
     * (dieselbe Tokenisierung, Elo 1500/1500).
     */
    @Test
    fun `echtes Netz waehlt die Referenzzuege`() {
        val path = System.getProperty("maia.model").orEmpty()
        assumeTrue("Kein Maia-Netz angegeben (maia.model)", path.isNotBlank())
        val model = File(path)
        assumeTrue("Maia-Netz nicht gefunden: $path", model.exists())

        val expected = linkedMapOf(
            startFen to "e2e4",
            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3" to "g8f6",
            "8/8/8/8/8/8/6k1/4K2R w K - 0 1" to "h1f1",
            "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1" to "e2e3",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3" to "e5f6",
            "8/PPPk4/8/8/8/8/4Kppp/8 w - - 0 1" to "c7c8q",
        )

        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
        }
        env.createSession(model.absolutePath, options).use { session ->
            assertEquals(
                listOf("tokens", "elo_self", "elo_oppo").sorted(),
                session.inputNames.map { it }.sorted(),
            )
            for ((fen, move) in expected) {
                assertEquals(fen, move, evaluate(env, session, fen))
            }
        }
        options.close()
    }

    private fun evaluate(env: OrtEnvironment, session: OrtSession, fen: String): String {
        val tokens = Maia3Encoding.tokens(fen)
        val logits = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(tokens),
            longArrayOf(1, 64, Maia3Encoding.BOARD_CHANNELS.toLong()),
        ).use { tokenTensor ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(1500f)), longArrayOf(1)).use { eloSelf ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(1500f)), longArrayOf(1)).use { eloOppo ->
                    session.run(mapOf("tokens" to tokenTensor, "elo_self" to eloSelf, "elo_oppo" to eloOppo))
                        .use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val policy = result.get(0).value as Array<FloatArray>
                            policy[0].copyOf(Maia3Encoding.MOVE_COUNT)
                        }
                }
            }
        }
        return Maia3Encoding.bestMove(fen, logits) ?: error("Kein Zug für $fen")
    }
}
