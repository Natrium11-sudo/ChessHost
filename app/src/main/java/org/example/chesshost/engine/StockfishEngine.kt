package org.example.chesshost.engine

import android.util.Log
import org.example.chesshost.engine.packs.UciConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Beliebige UCI-Engine als eigener Prozess. Die Standard-Engine wird als
 * jniLibs/<abi>/libstockfish.so ausgeliefert, von Android nach
 * nativeLibraryDir entpackt und über stdin/stdout angesprochen.
 *
 * Jede Instanz besitzt einen eigenen Prozess und einen eigenen Reader-Thread;
 * zwei Instanzen stören sich daher nicht.
 *
 * @param binaryFile ausführbare Datei (aus nativeLibraryDir oder, auf älteren
 *   Android-Versionen, aus einem installierten Paket)
 * @param displayName Name für die Oberfläche
 * @param uci Startparameter (Threads, Hash); Engines ohne diese Optionen
 *   ignorieren die Kommandos folgenlos
 */
class StockfishEngine(
    private val binaryFile: File,
    override val displayName: String = "Stockfish 17.1",
    private val uci: UciConfig = UciConfig(),
) : ChessEngine {

    private var process: Process? = null
    private var reader: Thread? = null
    private val output = LinkedBlockingQueue<String>()

    @Volatile
    private var running = false

    private var currentSkill: Int = -1
    private var currentVariant: String? = null

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext true
        if (!binaryFile.exists() || !binaryFile.canExecute()) {
            Log.w(TAG, "Stockfish-Binary fehlt oder ist nicht ausführbar: $binaryFile")
            return@withContext false
        }

        try {
            val p = ProcessBuilder(binaryFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            Log.i(TAG, "Prozess gestartet: ${binaryFile.absolutePath}")
            process = p
            running = true
            output.clear()
            reader = thread(isDaemon = true, name = "stockfish-reader") {
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        if (running) output.offer(line)
                    }
                    Log.w(TAG, "Engine-Stdout geschlossen (exit=${runCatching { p.exitValue() }.getOrNull()})")
                } catch (t: Throwable) {
                    Log.w(TAG, "Reader beendet", t)
                }
            }

            send("uci")
            if (awaitLine({ it == "uciok" }, 25_000) == null) {
                Log.w(TAG, "uciok nicht erhalten (alive=${p.isAlive})")
                closeProcess()
                return@withContext false
            }
            send("setoption name Threads value ${uci.threads}")
            if (uci.hashMb > 0) send("setoption name Hash value ${uci.hashMb}")
            send("setoption name Ponder value false")
            // Nachgeladene Netze: Pfad muss absolut sein. Ein Netz der falschen
            // Architektur beendet die Engine noch vor dem ersten readyok.
            uci.evalFile?.let { send("setoption name EvalFile value $it") }
            uci.evalFileSmall?.let { send("setoption name EvalFileSmall value $it") }
            send("isready")
            // 75-MB-Netze brauchen auf langsamen Geräten Zeit zum Laden.
            val ready = awaitLine({ it == "readyok" }, 30_000) != null
            if (!ready) closeProcess()
            ready
        } catch (t: Throwable) {
            Log.e(TAG, "Stockfish konnte nicht gestartet werden", t)
            closeProcess()
            false
        }
    }

    override suspend fun bestMove(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): String? = withContext(Dispatchers.IO) {
        val p = process
        if (p == null || !p.isAlive) return@withContext null

        if (currentSkill != config.skillLevel) {
            send("setoption name Skill Level value ${config.skillLevel}")
            currentSkill = config.skillLevel
        }
        // Schach960: Option umschalten und ab dann Rochaden als König→Turm
        // schicken. Die FEN kommt in Shredder-Schreibweise, die Stockfish so
        // versteht; ein Umschalten mitten in der Partie ist nicht nötig, weil
        // eine Instanz immer nur eine Variante spielt.
        if (currentVariant != config.variant) {
            send("setoption name UCI_Chess960 value ${config.variant == "chess960"}")
            send("isready")
            awaitLine({ it == "readyok" }, 10_000)
            currentVariant = config.variant
        }

        drain()
        val position = buildString {
            append("position fen ").append(startFen)
            if (uciHistory.isNotEmpty()) {
                append(" moves ")
                append(uciHistory.joinToString(" "))
            }
        }
        send(position)
        send("go movetime ${config.moveTimeMs}")

        val line = awaitLine({ it.startsWith("bestmove") }, config.moveTimeMs + 25_000)
        val uci = line?.split(' ')?.getOrNull(1)
        if (uci.isNullOrBlank() || uci == "(none)") null else uci
    }

    override suspend fun evaluate(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): EngineEval? = withContext(Dispatchers.IO) {
        val p = process
        if (p == null || !p.isAlive) return@withContext null

        if (currentSkill != config.skillLevel) {
            send("setoption name Skill Level value ${config.skillLevel}")
            currentSkill = config.skillLevel
        }
        if (currentVariant != config.variant) {
            send("setoption name UCI_Chess960 value ${config.variant == "chess960"}")
            send("isready")
            awaitLine({ it == "readyok" }, 10_000)
            currentVariant = config.variant
        }

        drain()
        val position = buildString {
            append("position fen ").append(startFen)
            if (uciHistory.isNotEmpty()) {
                append(" moves ")
                append(uciHistory.joinToString(" "))
            }
        }
        send(position)
        send("go movetime ${config.moveTimeMs}")

        val blackToMove = currentFen.split(' ').getOrNull(1) == "b"
        var evaluation: EngineEval? = null
        var bestMove: String? = null
        val deadline = System.currentTimeMillis() + config.moveTimeMs + 25_000
        while (System.currentTimeMillis() < deadline) {
            val line = output.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (line.startsWith("bestmove")) {
                bestMove = line.split(' ').getOrNull(1)
                break
            }
            parseInfo(line, blackToMove)?.let { evaluation = it }
        }
        val move = bestMove?.takeIf { it.isNotBlank() && it != "(none)" }
        evaluation?.copy(bestMoveUci = move ?: evaluation.bestMoveUci)
            ?: move?.let { EngineEval(null, null, it, emptyList()) }
    }

    /** Liest eine `info depth … score cp/mate … pv …`-Zeile aus. */
    private fun parseInfo(line: String, blackToMove: Boolean): EngineEval? {
        if (!line.startsWith("info ") || !line.contains(" score ")) return null
        val tokens = line.split(' ')
        val depth = tokens.indexOf("depth").takeIf { it >= 0 }
            ?.let { tokens.getOrNull(it + 1)?.toIntOrNull() } ?: 0
        val scoreAt = tokens.indexOf("score")
        if (scoreAt < 0) return null
        val cp: Int?
        val mate: Int?
        when (tokens.getOrNull(scoreAt + 1)) {
            "cp" -> {
                cp = tokens.getOrNull(scoreAt + 2)?.toIntOrNull()
                mate = null
            }
            "mate" -> {
                mate = tokens.getOrNull(scoreAt + 2)?.toIntOrNull()
                cp = null
            }
            else -> return null
        }
        val pvAt = tokens.indexOf("pv")
        val pv = if (pvAt >= 0) tokens.drop(pvAt + 1) else emptyList()
        // UCI bewertet aus Sicht der Seite am Zug.
        val sign = if (blackToMove) -1 else 1
        return EngineEval(
            centipawns = cp?.times(sign),
            mateIn = mate?.times(sign),
            bestMoveUci = null,
            pv = pv,
            depth = depth,
        )
    }

    override suspend fun stopSearch() = withContext(Dispatchers.IO) {
        if (process?.isAlive != true) return@withContext
        send("stop")
        send("isready")
        awaitLine({ it == "readyok" }, 5_000)
        drain()
    }

    override fun close() {
        closeProcess()
    }

    private fun closeProcess() {
        running = false
        val p = process ?: return
        try {
            if (p.isAlive) {
                p.outputStream.write("quit\n".toByteArray())
                p.outputStream.flush()
                if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroy()
            }
        } catch (t: Throwable) {
            p.destroy()
        } finally {
            process = null
            reader = null
            output.clear()
        }
    }

    private fun send(command: String) {
        val p = process ?: return
        try {
            p.outputStream.write((command + "\n").toByteArray())
            p.outputStream.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "Kommando konnte nicht gesendet werden: $command", t)
        }
    }

    private fun drain() {
        while (output.poll() != null) {
            // alten Output verwerfen
        }
    }

    private fun awaitLine(predicate: (String) -> Boolean, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = output.poll(100, TimeUnit.MILLISECONDS) ?: continue
            if (predicate(line)) return line
        }
        Log.w(TAG, "Timeout beim Warten auf Engine-Ausgabe (${timeoutMs}ms)")
        return null
    }

    companion object {
        private const val TAG = "StockfishEngine"

        fun binaryPath(nativeLibraryDir: String): File = File(nativeLibraryDir, "libstockfish.so")
    }
}
