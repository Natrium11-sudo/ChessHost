package org.example.schach.ui.play

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.schach.App
import org.example.schach.chess.Board
import org.example.schach.chess.Chess960Rules
import org.example.schach.chess.ChessGame
import org.example.schach.chess.Color
import org.example.schach.chess.GameEnd
import org.example.schach.chess.Move
import org.example.schach.chess.Piece
import org.example.schach.chess.RuleSet
import org.example.schach.chess.Rules
import org.example.schach.chess.TimeControl
import org.example.schach.chess.TimeControls
import org.example.schach.data.SavedGame
import org.example.schach.engine.ChessEngine
import org.example.schach.engine.Difficulty
import org.example.schach.engine.EngineConfig
import org.example.schach.engine.EngineEval
import org.example.schach.engine.SimpleEngine
import org.example.schach.engine.packs.EnginePack
import org.example.schach.engine.toConfig
import org.example.schach.ui.i18n.trNow
import org.example.schach.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class PlayUiState(
    val pieces: List<Piece?> = emptyList(),
    val variantId: String = "standard",
    val variantName: String = "Standard",
    val startFenLabel: String? = null,
    val sideToMove: Color = Color.WHITE,
    val selected: Int? = null,
    val legalTargets: Set<Int> = emptySet(),
    val lastMove: Move? = null,
    val checkSquare: Int? = null,
    val sanMoves: List<String> = emptyList(),
    val end: GameEnd? = null,
    val thinking: Boolean = false,
    val difficulty: Difficulty = Difficulty.CLUB,
    val engineName: String = "",
    val engineId: String? = null,
    val playerColor: Color = Color.WHITE,
    val promotionOptions: List<Move> = emptyList(),
    val engineError: String? = null,
    val timeControl: TimeControl = TimeControls.default,
    val whiteTimeMs: Long = TimeControls.default.initialMs,
    val blackTimeMs: Long = TimeControls.default.initialMs,
    /** Bewertung der aktuellen Stellung (Weiß positiv), null = noch keine. */
    val evaluation: EngineEval? = null,
    val analysing: Boolean = false,
    val hintSan: String? = null,
    val hintLine: List<String> = emptyList(),
    /** Halbzüge ohne Bauernzug oder Schlag (50-Züge-Regel). */
    val halfmoveClock: Int = 0,
)

class PlayViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val settingsRepository = container.settingsRepository
    private val gameRepository = container.gameRepository
    private val engineManager = container.engineManager

    private var rules: RuleSet = Rules.standard
    private var startFen: String = Board.START_FEN
    private var game = ChessGame()
    private var engine: ChessEngine? = null
    private var engineJob: Job? = null
    private var analysisJob: Job? = null
    private var savedGame = false

    /** Läuft, solange die Uhr der Spieler-Seite herunterzählt. */
    private var clockJob: Job? = null
    /** Restzeit der laufenden Seite zu Beginn ihres Zuges. */
    private var turnTimeMs = 0L
    private var turnStartedAt = 0L

    private val initialTimeControl = TimeControls.byId(settingsRepository.state.value.timeControlId)

    private val _ui = MutableStateFlow(
        PlayUiState(
            difficulty = settingsRepository.state.value.difficulty,
            engineName = engineManager.labelOf(settingsRepository.state.value.playEngineId),
            engineId = settingsRepository.state.value.playEngineId,
            timeControl = initialTimeControl,
            whiteTimeMs = initialTimeControl.initialMs,
            blackTimeMs = initialTimeControl.initialMs,
        ),
    )
    val ui: StateFlow<PlayUiState> = _ui.asStateFlow()

    /** Engines, die auf diesem Gerät gerade spielbar sind. */
    fun engines(): List<EnginePack> = engineManager.engines()

    init {
        refresh()
    }

    fun onSquareClick(square: Int) {
        val state = _ui.value
        if (state.end != null || state.thinking) return
        if (state.promotionOptions.isNotEmpty()) return
        if (game.board.sideToMove != state.playerColor) return

        val selected = state.selected
        if (selected != null) {
            val legal = game.legalMoves()
            var candidates = legal.filter { it.from == selected && it.to == square }
            if (candidates.isEmpty()) {
                // Schach960: Der König zieht auf g1/c1, der Turm steht aber oft
                // woanders. Wer den Turm antippt, will trotzdem rochieren.
                candidates = legal.filter { it.from == selected && it.isCastle && it.castleRook == square }
            }
            when {
                candidates.size > 1 -> {
                    _ui.update { it.copy(promotionOptions = candidates) }
                    return
                }
                candidates.size == 1 -> {
                    makePlayerMove(candidates.first())
                    return
                }
            }
        }

        val piece = game.board.pieceAt(square)
        if (piece != null && piece.color == state.playerColor) {
            val targets = game.legalMoves().filter { it.from == square }.map { it.to }.toSet()
            _ui.update { it.copy(selected = square, legalTargets = targets) }
        } else {
            _ui.update { it.copy(selected = null, legalTargets = emptySet()) }
        }
    }

    fun choosePromotion(move: Move) {
        _ui.update { it.copy(promotionOptions = emptyList()) }
        makePlayerMove(move)
    }

    fun cancelPromotion() {
        _ui.update { it.copy(promotionOptions = emptyList()) }
    }

    /** Startet eine Partie neu – von derselben Stellung wie zuvor. */
    fun newGame() = startFrom(startFen, rules.id)

    /**
     * Beginnt eine Partie von einer beliebigen Stellung (Stellungseditor,
     * gespeicherte Stellung oder Schach960).
     */
    fun startFrom(fen: String, variantId: String) {
        stopClock()
        engineJob?.cancel()
        viewModelScope.launch { engine?.stopSearch() }
        val nextRules = Rules.byId(variantId)
        val board = runCatching { Board.fromFen(fen, nextRules) }.getOrNull()
        if (board == null) {
            _ui.update { it.copy(engineError = "Die Stellung ist ungültig.") }
            return
        }
        rules = nextRules
        startFen = board.fen()
        game = ChessGame(startFen, rules)
        savedGame = false
        val timeControl = _ui.value.timeControl
        _ui.update {
            it.copy(
                variantId = rules.id,
                variantName = rules.name,
                startFenLabel = startFen.takeIf { it != Board.START_FEN },
                whiteTimeMs = timeControl.initialMs,
                blackTimeMs = timeControl.initialMs,
                selected = null,
                legalTargets = emptySet(),
                promotionOptions = emptyList(),
                engineError = null,
                thinking = false,
            )
        }
        refresh()
        startPlayerClock()
    }

    /** Neue zufällige Schach960-Stellung. */
    fun newChess960Game() {
        val fen = Chess960Rules.randomStartFen(Random.Default)
        startFrom(fen, Chess960Rules.id)
    }

    /** Startet von der Standardstellung (nach einer Partie von einer Stellung). */
    fun startStandard() = startFrom(Board.START_FEN, StandardRulesId)

    /**
     * Regelwerk der nächsten Partie. Wechselt die Auswahl, beginnt sofort eine
     * neue Partie von der passenden Grundstellung (Standard bzw. zufälliges 960).
     */
    fun setVariant(variantId: String) {
        when {
            variantId == Chess960Rules.id -> if (rules.id != Chess960Rules.id) newChess960Game()
            variantId == StandardRulesId ->
                if (rules.id != StandardRulesId || startFen != Board.START_FEN) startStandard()
            else -> startFrom(Board.START_FEN, variantId)
        }
    }

    private var appliedStartSpec: String? = null

    /**
     * Übernimmt die Startstellung aus der Navigation. Wird nur einmal
     * ausgewertet, damit ein Neuaufbau des Screens die Partie nicht zurücksetzt.
     */
    fun applyStartSpec(spec: String?) {
        if (spec.isNullOrBlank() || spec == appliedStartSpec) return
        appliedStartSpec = spec
        val decoded = Routes.decodeStart(spec) ?: return
        startFrom(decoded.second, decoded.first)
    }

    fun undo() {
        // Die Uhr läuft weiter: startPlayerClock übernimmt die bisher
        // verstrichene Zeit, statt sie zu erstatten.
        engineJob?.cancel()
        viewModelScope.launch { engine?.stopSearch() }
        if (game.moves.isEmpty()) return
        game.undo()
        if (game.moves.isNotEmpty() && game.board.sideToMove != _ui.value.playerColor) {
            game.undo()
        }
        _ui.update { it.copy(selected = null, legalTargets = emptySet(), thinking = false) }
        refresh()
        startPlayerClock()
    }

    fun resign() {
        if (game.end != null) return
        stopClock()
        engineJob?.cancel()
        viewModelScope.launch { engine?.stopSearch() }
        game.resign(_ui.value.playerColor)
        refresh(thinking = false)
    }

    /** Beendet die Partie als vereinbartes Remis. */
    fun offerDraw() {
        if (game.end != null) return
        stopClock()
        engineJob?.cancel()
        viewModelScope.launch { engine?.stopSearch() }
        game.agreeDraw()
        refresh(thinking = false)
    }

    /**
     * Bewertet die aktuelle Stellung mit der Engine und liefert Tipp und
     * Hauptvariante. Läuft nicht, während die Engine am Zug ist.
     */
    fun analyse() {
        val state = _ui.value
        if (state.analysing || state.thinking) return
        analysisJob?.cancel()
        _ui.update { it.copy(analysing = true, engineError = null) }
        analysisJob = viewModelScope.launch {
            val active = ensureEngine()
            if (active == null) {
                _ui.update { it.copy(analysing = false) }
                return@launch
            }
            val evaluation = active.evaluate(
                startFen = game.startFen,
                uciHistory = game.uciHistory(),
                currentFen = game.board.fen(),
                config = EngineConfig(skillLevel = 20, moveTimeMs = 1200, variant = rules.id),
            )
            val hintMove = evaluation?.bestMoveUci?.let { game.parseEngineMove(it) }
            val hintSan = hintMove?.let { rules.san(game.board, it, game.legalMoves()) }
            val line = evaluation?.pv?.let { pvToSan(it) }.orEmpty()
            _ui.update {
                it.copy(
                    analysing = false,
                    evaluation = evaluation,
                    hintSan = hintSan,
                    hintLine = line,
                )
            }
        }
    }

    /** Wandelt eine UCI-Hauptvariante in SAN-Züge für die Anzeige um. */
    private fun pvToSan(pv: List<String>): List<String> {
        val copy = runCatching { ChessGame(game.board.fen(), rules) }.getOrNull() ?: return emptyList()
        val san = mutableListOf<String>()
        for (uci in pv.take(8)) {
            val move = copy.parseEngineMove(uci) ?: break
            if (!copy.makeMove(move)) break
            san += copy.sanMoves.lastOrNull() ?: break
        }
        return san
    }

    fun setDifficulty(difficulty: Difficulty) {
        settingsRepository.setDifficulty(difficulty)
        _ui.update { it.copy(difficulty = difficulty) }
    }

    /**
     * Wechselt die Bedenkzeit. Weil die Uhr zur Partie gehört, beginnt dabei
     * eine neue Partie von derselben Stellung mit der neuen Zeit.
     */
    fun setTimeControl(id: String) {
        val control = TimeControls.byId(id)
        if (control.id == _ui.value.timeControl.id) return
        settingsRepository.setTimeControlId(control.id)
        _ui.update { it.copy(timeControl = control) }
        startFrom(startFen, rules.id)
    }

    fun setEngine(packId: String) {
        settingsRepository.setPlayEngineId(packId)
        engineJob?.cancel()
        val previous = engine
        engine = null
        if (previous != null) {
            viewModelScope.launch {
                previous.stopSearch()
                engineManager.release(previous)
            }
        }
        _ui.update { it.copy(engineId = packId, engineName = engineManager.labelOf(packId)) }
    }

    private fun makePlayerMove(move: Move) {
        val state = _ui.value
        if (state.timeControl.clocked) {
            val player = state.playerColor
            val running = clockJob != null
            val remaining = if (running) clockRemaining() else clockOf(player)
            stopClock()
            if (remaining <= 0L) {
                game.timeout(player)
                refresh(thinking = false)
                return
            }
            // Aufschlag erst nach dem Zug – wie bei einer echten Schachuhr.
            setClock(player, remaining + state.timeControl.incrementMs)
        }
        if (!game.makeMove(move)) return
        _ui.update { it.copy(selected = null, legalTargets = emptySet(), promotionOptions = emptyList()) }
        refresh()
        if (game.end == null) startEngineMove()
    }

    private fun startEngineMove() {
        engineJob?.cancel()
        _ui.update { it.copy(thinking = true, engineError = null) }
        engineJob = viewModelScope.launch {
            // Ab hier läuft die Uhr der Engine – auch während sie noch startet.
            val thinkingStartedAt = SystemClock.elapsedRealtime()
            val active = ensureEngine()
            if (active == null) {
                _ui.update {
                    it.copy(
                        thinking = false,
                        engineError = it.engineError ?: "Engine konnte nicht gestartet werden.",
                    )
                }
                return@launch
            }
            val timeControl = _ui.value.timeControl
            val engineColor = if (_ui.value.playerColor == Color.WHITE) Color.BLACK else Color.WHITE
            val engineTime = clockOf(engineColor)
            val config = _ui.value.difficulty.toConfig(rules.id)
            // Mit Uhr bekommt die Engine nur einen Teil ihrer Restzeit je Zug,
            // sonst könnte sie die Partie über die Zeit hinaus denken.
            val budget = if (timeControl.clocked) {
                minOf(config.moveTimeMs, (engineTime / 30).coerceAtLeast(50L))
            } else {
                config.moveTimeMs
            }
            val best = active.bestMove(
                startFen = game.startFen,
                uciHistory = game.uciHistory(),
                currentFen = game.board.fen(),
                config = config.copy(moveTimeMs = budget),
            )
            if (timeControl.clocked) {
                val elapsed = SystemClock.elapsedRealtime() - thinkingStartedAt
                val remaining = (engineTime - elapsed + timeControl.incrementMs).coerceAtLeast(0)
                setClock(engineColor, remaining)
                if (remaining <= 0L) {
                    game.timeout(engineColor)
                    refresh(thinking = false)
                    return@launch
                }
            }
            val move = game.parseEngineMove(best)
            if (move != null && game.end == null && game.board.sideToMove != _ui.value.playerColor) {
                game.makeMove(move)
                refresh(thinking = false)
                startPlayerClock()
            } else {
                _ui.update {
                    it.copy(
                        thinking = false,
                        // Ohne Zug (etwa wenn die LLM-API nicht antwortet) bleibt
                        // die Partie stehen – das muss sichtbar sein.
                        engineError = if (move == null) {
                            trNow("%s lieferte keinen Zug.", active.displayName)
                        } else {
                            it.engineError
                        },
                    )
                }
            }
        }
    }

    /** Restzeit einer Seite aus dem Zustand. */
    private fun clockOf(color: Color): Long =
        if (color == Color.WHITE) _ui.value.whiteTimeMs else _ui.value.blackTimeMs

    private fun setClock(color: Color, remaining: Long) {
        val value = remaining.coerceAtLeast(0)
        _ui.update {
            if (color == Color.WHITE) it.copy(whiteTimeMs = value) else it.copy(blackTimeMs = value)
        }
    }

    /** Restzeit der laufenden Seite, seit ihr Zug begonnen hat. */
    private fun clockRemaining(): Long =
        (turnTimeMs - (SystemClock.elapsedRealtime() - turnStartedAt)).coerceAtLeast(0)

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    /** Startet die Uhr des Spielers, wenn er am Zug ist und eine Uhr läuft. */
    private fun startPlayerClock() {
        val state = _ui.value
        val player = state.playerColor
        // Läuft die Uhr schon, zählt die bisher verstrichene Zeit weiter (etwa
        // nach einem Zurücknehmen), statt sie zu erstatten.
        val carried = if (clockJob != null) clockRemaining() else null
        stopClock()
        val control = state.timeControl
        if (!control.clocked || game.end != null) return
        if (game.board.sideToMove != player) return
        if (carried != null) setClock(player, carried)
        turnTimeMs = carried ?: clockOf(player)
        turnStartedAt = SystemClock.elapsedRealtime()
        clockJob = viewModelScope.launch {
            // Die Anzeige zählt selbst herunter (rememberCountdown); hier wird
            // nur der echte Zeitablauf geprüft, damit das ViewModel nicht alle
            // 200 ms neue Zustände schickt.
            while (isActive) {
                if (clockRemaining() <= 0L) {
                    setClock(player, 0L)
                    game.timeout(player)
                    stopClock()
                    refresh(thinking = false)
                    return@launch
                }
                delay(200)
            }
        }
    }

    private suspend fun ensureEngine(): ChessEngine? {
        val current = engine
        if (current != null) return current

        val pack = engineManager.pack(_ui.value.engineId)
        if (pack != null && !pack.supportsVariant(rules.id)) {
            _ui.update {
                it.copy(
                    thinking = false,
                    engineError = trNow(
                        "%s kann %s nicht spielen. Bitte eine andere Engine wählen.",
                        pack.title,
                        rules.name,
                    ),
                )
            }
            return null
        }

        val created = engineManager.createEngine(_ui.value.engineId)
        val active = if (created != null && created.start()) {
            created
        } else {
            created?.let { engineManager.release(it) }
            SimpleEngine().also { it.start() }
        }
        engine = active
        _ui.update { it.copy(engineName = active.displayName) }
        return active
    }

    private fun refresh(thinking: Boolean = _ui.value.thinking) {
        val board = game.board
        val checkSquare = if (board.isInCheck(board.sideToMove)) board.kingSquare(board.sideToMove) else null
        _ui.update {
            it.copy(
                pieces = board.snapshot(),
                sideToMove = board.sideToMove,
                sanMoves = game.sanMoves.toList(),
                end = game.end,
                lastMove = game.moves.lastOrNull(),
                checkSquare = checkSquare,
                thinking = thinking,
                halfmoveClock = board.fen().split(' ').getOrNull(4)?.toIntOrNull() ?: 0,
                // Eine neue Stellung macht die alte Bewertung ungültig.
                evaluation = null,
                hintSan = null,
                hintLine = emptyList(),
            )
        }
        if (game.end != null) {
            _ui.update { it.copy(selected = null, legalTargets = emptySet()) }
            persistFinishedGame()
        }
    }

    private fun persistFinishedGame() {
        val end = game.end ?: return
        if (savedGame) return
        savedGame = true

        val difficulty = _ui.value.difficulty
        // Im PGN steht immer der Name der Engine, notfalls der der Auswahl.
        val engineName = _ui.value.engineName.ifBlank { engineManager.labelOf(_ui.value.engineId) }
        val engineLabel = "$engineName (${difficulty.label})"
        val whiteName = if (_ui.value.playerColor == Color.WHITE) PLAYER_NAME else engineLabel
        val blackName = if (_ui.value.playerColor == Color.BLACK) PLAYER_NAME else engineLabel
        val pgn = game.toPgn(
            whiteName = whiteName,
            blackName = blackName,
            extraTags = mapOf("Mode" to "Spieler gegen Engine"),
        )
        val saved = SavedGame(
            whiteName = whiteName,
            blackName = blackName,
            mode = MODE_VS_ENGINE,
            result = end.result.pgn,
            termination = end.reason.label,
            pgn = pgn,
            moveCount = game.moves.size,
            playedAt = System.currentTimeMillis(),
            variant = rules.id,
        )
        viewModelScope.launch { gameRepository.save(saved) }
    }

    override fun onCleared() {
        stopClock()
        engineJob?.cancel()
        analysisJob?.cancel()
        engineManager.closeAll()
        super.onCleared()
    }

    companion object {
        const val PLAYER_NAME = "Spieler"
        const val MODE_VS_ENGINE = "vs_engine"
        private const val StandardRulesId = "standard"
    }
}
