package org.example.chesshost.ui.match

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.chesshost.App
import org.example.chesshost.chess.Board
import org.example.chesshost.chess.Chess960Rules
import org.example.chesshost.chess.ChessGame
import org.example.chesshost.chess.Color
import org.example.chesshost.chess.GameEnd
import org.example.chesshost.chess.Move
import org.example.chesshost.chess.Piece
import org.example.chesshost.chess.RuleSet
import org.example.chesshost.chess.Rules
import org.example.chesshost.chess.TimeControl
import org.example.chesshost.chess.TimeControls
import org.example.chesshost.data.SavedGame
import org.example.chesshost.data.SavedPosition
import org.example.chesshost.engine.ChessEngine
import org.example.chesshost.engine.Difficulty
import org.example.chesshost.engine.SimpleEngine
import org.example.chesshost.engine.packs.EnginePack
import org.example.chesshost.engine.toConfig
import org.example.chesshost.ui.i18n.trNow
import org.example.chesshost.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class EngineMatchUiState(
    val pieces: List<Piece?> = emptyList(),
    val sideToMove: Color = Color.WHITE,
    val sanMoves: List<String> = emptyList(),
    val lastMove: Move? = null,
    val checkSquare: Int? = null,
    val end: GameEnd? = null,
    val running: Boolean = false,
    val paused: Boolean = false,
    val thinking: Boolean = false,
    val whiteDifficulty: Difficulty = Difficulty.CLUB,
    val blackDifficulty: Difficulty = Difficulty.ADVANCED,
    val whiteEngine: String = "",
    val blackEngine: String = "",
    val whiteEngineId: String? = null,
    val blackEngineId: String? = null,
    val timeControl: TimeControl = TimeControls.default,
    val whiteTimeMs: Long = TimeControls.default.initialMs,
    val blackTimeMs: Long = TimeControls.default.initialMs,
    val variantId: String = Rules.standard.id,
    val variantName: String = Rules.standard.name,
    /** Startstellung als FEN; null = Grundstellung des Regelwerks. */
    val startFenLabel: String? = null,
    val positions: List<SavedPosition> = emptyList(),
    val error: String? = null,
)

/**
 * Steuert eine Partie zwischen zwei Engines. Beide Seiten haben eine eigene
 * Engine-Instanz (eigener Prozess) und eine eigene Uhr.
 */
class EngineMatchViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val engineManager = container.engineManager
    private val gameRepository = container.gameRepository
    private val settingsRepository = container.settingsRepository
    private val positionRepository = container.positionRepository

    private var rules: RuleSet = Rules.standard

    /** Eigene Startstellung; null = Grundstellung des gewählten Regelwerks. */
    private var startFen: String? = null
    private var game = ChessGame()
    private var whiteEngine: ChessEngine? = null
    private var blackEngine: ChessEngine? = null
    private var loopJob: Job? = null
    private var saved = false

    /** Laufende Suche, damit eine Pause die verstrichene Denkzeit abrechnet. */
    private var searchStartedAt = 0L
    private var searchSide: Color? = null
    private var searchRemainingBefore = 0L

    private val initialTimeControl = TimeControls.byId(settingsRepository.state.value.timeControlId)

    private val _ui = MutableStateFlow(
        EngineMatchUiState(
            whiteEngine = engineManager.labelOf(settingsRepository.state.value.whiteEngineId),
            blackEngine = engineManager.labelOf(settingsRepository.state.value.blackEngineId),
            whiteEngineId = settingsRepository.state.value.whiteEngineId,
            blackEngineId = settingsRepository.state.value.blackEngineId,
            whiteDifficulty = Difficulty.CLUB,
            blackDifficulty = Difficulty.ADVANCED,
            timeControl = initialTimeControl,
            whiteTimeMs = initialTimeControl.initialMs,
            blackTimeMs = initialTimeControl.initialMs,
        ),
    )
    val ui: StateFlow<EngineMatchUiState> = _ui.asStateFlow()

    /** Engines, die auf diesem Gerät gerade spielbar sind. */
    fun engines(): List<EnginePack> = engineManager.engines()

    /**
     * Startet das Duell von einer eigenen Stellung bzw. in einem anderen
     * Regelwerk. [fen] null bedeutet: Grundstellung des Regelwerks.
     */
    fun setStart(fen: String?, variantId: String) {
        if (_ui.value.running) return
        val next = Rules.byId(variantId)
        val board = fen?.let { runCatching { Board.fromFen(it, next) }.getOrNull() }
        if (fen != null && board == null) {
            _ui.update { it.copy(error = "Die Stellung ist ungültig.") }
            return
        }
        val problems = board?.let { next.validate(it) }.orEmpty()
        if (problems.isNotEmpty()) {
            _ui.update { it.copy(error = problems.first()) }
            return
        }

        rules = next
        startFen = board?.fen()?.takeIf { it != Board.initial(next).fen() }

        // Engines, die das Regelwerk nicht können (etwa Maia bei Schach960),
        // werden gleich durch eine passende ersetzt.
        val supported = engineManager.engines().filter { it.supportsVariant(next.id) }
        _ui.update { current ->
            val whiteId = current.whiteEngineId?.takeIf { id -> supported.any { it.id == id } }
                ?: supported.firstOrNull()?.id
            val blackId = current.blackEngineId?.takeIf { id -> supported.any { it.id == id } }
                ?: supported.lastOrNull()?.id
            current.copy(
                variantId = next.id,
                variantName = next.name,
                startFenLabel = startFen,
                whiteEngineId = whiteId,
                whiteEngine = engineManager.labelOf(whiteId),
                blackEngineId = blackId,
                blackEngine = engineManager.labelOf(blackId),
                error = null,
            )
        }
        resetGame()
    }

    /** Neue zufällige Schach960-Stellung als Startpunkt. */
    fun newChess960Start() {
        setStart(Chess960Rules.randomStartFen(Random.Default), Chess960Rules.id)
    }

    private var appliedStartSpec: String? = null

    /** Startstellung aus der Navigation (Stellungen → Engine-Duell). */
    fun applyStartSpec(spec: String?) {
        if (spec.isNullOrBlank() || spec == appliedStartSpec) return
        appliedStartSpec = spec
        val decoded = Routes.decodeStart(spec) ?: return
        setStart(decoded.second, decoded.first)
    }

    fun setWhiteEngine(packId: String) {
        settingsRepository.setWhiteEngineId(packId)
        releaseEngine(Color.WHITE)
        _ui.update { it.copy(whiteEngineId = packId, whiteEngine = engineManager.labelOf(packId)) }
    }

    fun setBlackEngine(packId: String) {
        settingsRepository.setBlackEngineId(packId)
        releaseEngine(Color.BLACK)
        _ui.update { it.copy(blackEngineId = packId, blackEngine = engineManager.labelOf(packId)) }
    }

    init {
        refresh()
        viewModelScope.launch {
            positionRepository.observeAll().collect { list -> _ui.update { it.copy(positions = list) } }
        }
    }

    fun setWhiteDifficulty(difficulty: Difficulty) {
        _ui.update { it.copy(whiteDifficulty = difficulty) }
    }

    fun setBlackDifficulty(difficulty: Difficulty) {
        _ui.update { it.copy(blackDifficulty = difficulty) }
    }

    /** Bedenkzeit des Duells; eine neue Wahl setzt die Partie zurück. */
    fun setTimeControl(id: String) {
        if (_ui.value.running) return
        val control = TimeControls.byId(id)
        if (control.id == _ui.value.timeControl.id) return
        settingsRepository.setTimeControlId(control.id)
        _ui.update { it.copy(timeControl = control) }
        resetGame()
    }

    fun start() {
        if (_ui.value.running) return
        if (game.end != null) newGame()
        _ui.update { it.copy(running = true, paused = false, error = null) }
        launchLoop()
    }

    fun pause() {
        if (!_ui.value.running) return
        // Die Engine hört sofort auf zu rechnen – die Uhr hat bis hierher aber
        // weitergezählt, also wird die Zeit vor dem Abbrechen abgezogen.
        chargeRunningSearch()
        loopJob?.cancel()
        loopJob = null
        _ui.update { it.copy(running = false, paused = game.end == null, thinking = false) }
        viewModelScope.launch {
            whiteEngine?.stopSearch()
            blackEngine?.stopSearch()
        }
    }

    /**
     * Rechnet die Denkzeit einer gerade laufenden Suche ab, etwa beim Pausieren:
     * Ein Aufschlag gibt es dafür nicht, weil kein Zug gespielt wurde.
     */
    private fun chargeRunningSearch() {
        val side = searchSide ?: return
        searchSide = null
        if (!_ui.value.timeControl.clocked) return
        val elapsed = (SystemClock.elapsedRealtime() - searchStartedAt).coerceAtLeast(0)
        val remaining = (searchRemainingBefore - elapsed).coerceAtLeast(0)
        _ui.update {
            if (side == Color.WHITE) it.copy(whiteTimeMs = remaining) else it.copy(blackTimeMs = remaining)
        }
        if (remaining <= 0L) {
            game.timeout(side)
            _ui.update { it.copy(running = false, paused = false, thinking = false) }
            refresh()
        }
    }

    fun resume() {
        if (_ui.value.running || game.end != null) return
        _ui.update { it.copy(running = true, paused = false, error = null) }
        launchLoop()
    }

    fun abort() {
        loopJob?.cancel()
        loopJob = null
        viewModelScope.launch {
            whiteEngine?.stopSearch()
            blackEngine?.stopSearch()
        }
        game.abort()
        _ui.update { it.copy(running = false, paused = false, thinking = false) }
        refresh()
    }

    /** Neue Partie von derselben Startstellung aus. */
    fun newGame() = resetGame()

    private fun resetGame() {
        loopJob?.cancel()
        loopJob = null
        viewModelScope.launch {
            whiteEngine?.stopSearch()
            blackEngine?.stopSearch()
        }
        val fen = startFen ?: Board.initial(rules).fen()
        game = runCatching { ChessGame(fen, rules) }.getOrElse { ChessGame() }
        saved = false
        val timeControl = _ui.value.timeControl
        _ui.update {
            it.copy(
                running = false,
                paused = false,
                thinking = false,
                end = null,
                whiteTimeMs = timeControl.initialMs,
                blackTimeMs = timeControl.initialMs,
                error = null,
            )
        }
        refresh()
    }

    private fun launchLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            // Die Uhr der Seite am Zug läuft ab dem Ende des vorigen Zuges – so
            // stimmt sie mit der Anzeige überein, die dort zu ticken beginnt.
            var turnStartedAt = SystemClock.elapsedRealtime()
            while (isActive && game.end == null && _ui.value.running) {
                val side = game.board.sideToMove
                val state = _ui.value
                val control = state.timeControl
                val remainingBefore = if (side == Color.WHITE) state.whiteTimeMs else state.blackTimeMs
                if (control.clocked && remainingBefore <= 0) {
                    game.timeout(side)
                    break
                }

                // Ab hier läuft die Uhr dieser Seite – auch während die Engine
                // erst noch startet.
                searchStartedAt = turnStartedAt
                searchSide = side
                searchRemainingBefore = remainingBefore

                val engine = ensureEngine(side)
                if (engine == null) {
                    searchSide = null
                    _ui.update {
                        it.copy(running = false, thinking = false, error = "Engine konnte nicht gestartet werden.")
                    }
                    return@launch
                }

                _ui.update { it.copy(thinking = true) }
                val difficulty = if (side == Color.WHITE) state.whiteDifficulty else state.blackDifficulty
                // Mit Uhr denkt die Engine höchstens einen Bruchteil ihrer Restzeit.
                val budget = if (control.clocked) {
                    minOf(difficulty.moveTimeMs, (remainingBefore / 30).coerceAtLeast(50L))
                } else {
                    difficulty.moveTimeMs
                }
                val best = engine.bestMove(
                    startFen = game.startFen,
                    uciHistory = game.uciHistory(),
                    currentFen = game.board.fen(),
                    config = difficulty.toConfig(rules.id).copy(moveTimeMs = budget),
                )
                searchSide = null
                val elapsed = SystemClock.elapsedRealtime() - turnStartedAt
                val remainingAfter = if (control.clocked) {
                    (remainingBefore - elapsed + control.incrementMs).coerceAtLeast(0)
                } else {
                    remainingBefore
                }

                if (control.clocked) {
                    _ui.update {
                        if (side == Color.WHITE) {
                            it.copy(whiteTimeMs = remainingAfter)
                        } else {
                            it.copy(blackTimeMs = remainingAfter)
                        }
                    }

                    if (remainingAfter <= 0) {
                        game.timeout(side)
                        break
                    }
                }

                val move = game.parseEngineMove(best)
                if (move == null) {
                    _ui.update {
                        it.copy(
                            running = false,
                            thinking = false,
                            error = "Engine lieferte keinen gültigen Zug. Partie abgebrochen.",
                        )
                    }
                    game.abort()
                    break
                }

                game.makeMove(move)
                _ui.update { it.copy(thinking = false) }
                refresh()
                turnStartedAt = SystemClock.elapsedRealtime()
                if (game.end != null) break
                delay(180)
            }
            _ui.update { it.copy(running = false, thinking = false, paused = false) }
            refresh()
        }
    }

    /** Beendet die Engine einer Seite, damit die Auswahl beim nächsten Zug greift. */
    private fun releaseEngine(side: Color) {
        val engine = (if (side == Color.WHITE) whiteEngine else blackEngine) ?: return
        loopJob?.cancel()
        loopJob = null
        if (side == Color.WHITE) whiteEngine = null else blackEngine = null
        viewModelScope.launch {
            engine.stopSearch()
            engineManager.release(engine)
        }
        _ui.update { it.copy(running = false, paused = false, thinking = false) }
    }

    private suspend fun ensureEngine(side: Color): ChessEngine? {
        val existing = if (side == Color.WHITE) whiteEngine else blackEngine
        if (existing != null) return existing

        val packId = if (side == Color.WHITE) _ui.value.whiteEngineId else _ui.value.blackEngineId
        // Ohne Auswahl greift die Standard-Engine – die muss genauso passen.
        val pack = engineManager.pack(packId) ?: engineManager.defaultEngine()
        if (pack != null && !pack.supportsVariant(rules.id)) {
            _ui.update {
                it.copy(
                    running = false,
                    thinking = false,
                    error = trNow(
                        "%s kann %s nicht spielen. Bitte eine andere Engine wählen.",
                        pack.title,
                        rules.name,
                    ),
                )
            }
            return null
        }
        val created = engineManager.createEngine(packId)
        val active = if (created != null && created.start()) {
            created
        } else {
            created?.let { engineManager.release(it) }
            SimpleEngine().also { it.start() }
        }
        if (side == Color.WHITE) {
            whiteEngine = active
            _ui.update { it.copy(whiteEngine = active.displayName) }
        } else {
            blackEngine = active
            _ui.update { it.copy(blackEngine = active.displayName) }
        }
        return active
    }

    private fun refresh() {
        val board = game.board
        val checkSquare = if (board.isInCheck(board.sideToMove)) board.kingSquare(board.sideToMove) else null
        _ui.update {
            it.copy(
                pieces = board.snapshot(),
                sideToMove = board.sideToMove,
                sanMoves = game.sanMoves.toList(),
                lastMove = game.moves.lastOrNull(),
                checkSquare = checkSquare,
                end = game.end,
                thinking = if (game.end != null) false else it.thinking,
            )
        }
        if (game.end != null) persistFinishedGame()
    }

    private fun persistFinishedGame() {
        val end = game.end ?: return
        if (saved) return
        saved = true

        val state = _ui.value
        // Im PGN stehen die gewählten Engines, nicht nur "Engine A"/"Engine B".
        val whiteName = "${state.whiteEngine.ifBlank { "Engine A" }} (${state.whiteDifficulty.label})"
        val blackName = "${state.blackEngine.ifBlank { "Engine B" }} (${state.blackDifficulty.label})"
        val pgn = game.toPgn(
            whiteName = whiteName,
            blackName = blackName,
            extraTags = mapOf("Mode" to "Engine gegen Engine"),
        )
        val record = SavedGame(
            whiteName = whiteName,
            blackName = blackName,
            mode = MODE_ENGINE_MATCH,
            result = end.result.pgn,
            termination = end.reason.label,
            pgn = pgn,
            moveCount = game.moves.size,
            playedAt = System.currentTimeMillis(),
            variant = game.rules.id,
        )
        viewModelScope.launch { gameRepository.save(record) }
    }

    override fun onCleared() {
        loopJob?.cancel()
        engineManager.closeAll()
        super.onCleared()
    }

    companion object {
        const val MODE_ENGINE_MATCH = "engine_vs_engine"
    }
}
