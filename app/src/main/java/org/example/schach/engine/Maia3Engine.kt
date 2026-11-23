package org.example.schach.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import org.example.schach.engine.maia.Maia3Encoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

/**
 * Neuronale Engine auf Basis von ONNX Runtime. Das Netz wird als Datei aus dem
 * Store nachgeladen (siehe `EnginePack.files`); in der APK steckt nur die
 * Laufzeit und die Kodierung in [Maia3Encoding].
 *
 * Maia ist kein Suchprogramm: Sie sagt die Wahrscheinlichkeit jedes Zugs voraus,
 * mit der ein Mensch dieser Spielstärke ihn spielen würde. Es gibt daher keine
 * Bedenkzeit und keinen Skill-Level – der stärkste Hebel ist [elo].
 *
 * @param modelFile heruntergeladene `.onnx`-Datei
 * @param displayName Name für die Oberfläche
 * @param elo Spielstärke, auf die das Netz konditioniert wird
 * @param opponentElo angenommene Spielstärke des Gegners (ebenfalls Eingabe des Modells)
 */
class Maia3Engine(
    private val modelFile: File,
    override val displayName: String,
    private val elo: Int = DEFAULT_ELO,
    private val opponentElo: Int = elo,
) : ChessEngine {

    private var environment: OrtEnvironment? = null
    private var options: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (session != null) return@withContext true
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.w(TAG, "Netz fehlt: $modelFile")
            return@withContext false
        }

        try {
            val env = OrtEnvironment.getEnvironment()
            // `basic` statt `all`: aggressive fp16-Fusionen brechen dieses Netz.
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }
            val created = env.createSession(modelFile.absolutePath, sessionOptions)
            environment = env
            options = sessionOptions
            session = created
            Log.i(TAG, "Maia-Netz geladen: ${modelFile.name} (${modelFile.length() / 1024} KiB)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Netz konnte nicht geladen werden", t)
            close()
            false
        }
    }

    override suspend fun bestMove(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): String? = withContext(Dispatchers.Default) {
        val env = environment ?: return@withContext null
        val ortSession = session ?: return@withContext null
        coroutineContext.ensureActive()

        try {
            val tokens = Maia3Encoding.tokens(currentFen)
            val logits = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(tokens),
                longArrayOf(1, 64, Maia3Encoding.BOARD_CHANNELS.toLong()),
            ).use { tokenTensor ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(elo.toFloat())), longArrayOf(1))
                    .use { selfTensor ->
                        OnnxTensor.createTensor(
                            env,
                            FloatBuffer.wrap(floatArrayOf(opponentElo.toFloat())),
                            longArrayOf(1),
                        ).use { opponentTensor ->
                            val feeds = mapOf(
                                "tokens" to tokenTensor,
                                "elo_self" to selfTensor,
                                "elo_oppo" to opponentTensor,
                            )
                            ortSession.run(feeds).use { result ->
                                @Suppress("UNCHECKED_CAST")
                                val policy = result.get(0).value as Array<FloatArray>
                                policy[0].copyOf(Maia3Encoding.MOVE_COUNT)
                            }
                        }
                    }
            }
            coroutineContext.ensureActive()
            Maia3Encoding.bestMove(currentFen, logits)
        } catch (t: Throwable) {
            Log.e(TAG, "Auswertung fehlgeschlagen", t)
            null
        }
    }

    /** Eine Auswertung dauert Millisekunden – es gibt nichts abzubrechen. */
    override suspend fun stopSearch() = Unit

    override fun close() {
        runCatching { session?.close() }
        runCatching { options?.close() }
        session = null
        options = null
        environment = null
    }

    companion object {
        private const val TAG = "Maia3Engine"
        const val DEFAULT_ELO = 1500
    }
}
