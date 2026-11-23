package org.example.schach.ui.tournament

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.schach.App
import org.example.schach.chess.RuleSet
import org.example.schach.chess.Rules
import org.example.schach.chess.TimeControl
import org.example.schach.chess.TimeControls
import org.example.schach.data.Tournament
import org.example.schach.data.TournamentGame
import org.example.schach.data.TournamentParticipant
import org.example.schach.engine.Difficulty
import org.example.schach.engine.packs.EnginePack
import org.example.schach.engine.tournament.TournamentLiveGame
import org.example.schach.engine.tournament.TournamentProgress
import org.example.schach.engine.tournament.TournamentRunState
import org.example.schach.service.TournamentService
import org.example.schach.ui.i18n.trNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Liste aller Turniere plus Zustand des laufenden. */
class TournamentListViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container

    val tournaments: StateFlow<List<Tournament>> = container.tournamentRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val run: StateFlow<TournamentRunState?> = container.tournamentController.state

    fun delete(id: Long) {
        if (run.value?.tournamentId == id) {
            container.tournamentController.abort()
            TournamentService.stop(getApplication())
        }
        viewModelScope.launch { container.tournamentRepository.delete(id) }
    }
}

data class SetupState(
    val name: String = "",
    val available: List<EnginePack> = emptyList(),
    val selected: List<String> = emptyList(),
    val difficulties: Map<String, Difficulty> = emptyMap(),
    val games: Int = 20,
    val useOpenings: Boolean = true,
    val moveTimeMs: Long = 500L,
    /** Bedenkzeit der Partien (Bullet, Blitz, Schnellschach …). */
    val timeControl: TimeControl = TimeControls.default,
    val variant: String = "standard",
    val variants: List<RuleSet> = Rules.all,
    val starting: Boolean = false,
    val error: String? = null,
    val runActive: Boolean = false,
) {
    val canStart: Boolean get() = selected.size >= 2 && !starting && !runActive
}

/** Einstellungen für ein neues Turnier. */
class TournamentSetupViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val engineManager = container.engineManager
    private val controller = container.tournamentController

    private val _state = MutableStateFlow(
        SetupState(
            name = "",
            available = engineManager.engines(),
            games = 20,
            moveTimeMs = 500L,
            timeControl = TimeControls.byId(container.settingsRepository.state.value.timeControlId),
        ),
    )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { run ->
                _state.update { it.copy(runActive = run?.running == true) }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun toggleEngine(packId: String) = _state.update { current ->
        val nextSelected = when {
            packId in current.selected -> current.selected - packId
            else -> current.selected + packId
        }
        current.copy(
            selected = nextSelected,
            difficulties = current.difficulties + (packId to (current.difficulties[packId] ?: Difficulty.CLUB)),
        )
    }

    fun setDifficulty(packId: String, difficulty: Difficulty) = _state.update {
        it.copy(difficulties = it.difficulties + (packId to difficulty))
    }

    fun setGames(count: Int) = _state.update { it.copy(games = count) }

    /**
     * Wechselt das Regelwerk. Engines, die es nicht spielen können (etwa Maia
     * bei Schach960), fliegen aus der Auswahl – sonst bricht das Turnier erst
     * beim Start ab.
     */
    fun setVariant(variantId: String) {
        _state.update { current ->
            val supported = engineManager.engines().filter { it.supportsVariant(variantId) }
            current.copy(
                variant = variantId,
                available = supported,
                selected = current.selected.filter { id -> supported.any { it.id == id } },
                error = null,
            )
        }
    }

    fun setUseOpenings(enabled: Boolean) = _state.update { it.copy(useOpenings = enabled) }

    fun setMoveTime(ms: Long) = _state.update { it.copy(moveTimeMs = ms) }

    fun setTimeControl(id: String) =
        _state.update { it.copy(timeControl = TimeControls.byId(id)) }

    fun start(onStarted: (Long) -> Unit) {
        val current = _state.value
        if (!current.canStart) return
        _state.update { it.copy(starting = true, error = null) }

        val participants = current.selected.mapNotNull { packId ->
            val pack = current.available.firstOrNull { it.id == packId } ?: return@mapNotNull null
            val difficulty = current.difficulties[packId] ?: Difficulty.CLUB
            TournamentParticipant(
                id = pack.id,
                // Name und Version getrennt übersetzen, damit auch der
                // Teilnehmername in der gewählten Sprache erscheint.
                label = if (pack.version.isBlank()) {
                    trNow(pack.name)
                } else {
                    trNow("%s %s", trNow(pack.name), trNow(pack.version))
                },
                difficulty = difficulty.name,
                skillLevel = difficulty.skillLevel,
                moveTimeMs = current.moveTimeMs,
                threads = pack.uci?.threads ?: 1,
                hashMb = pack.uci?.hashMb ?: 32,
                elo = pack.elo,
            )
        }

        viewModelScope.launch {
            val name = current.name.trim().ifEmpty { defaultName() }
            val id = container.tournamentController.start(
                name = name,
                participants = participants,
                plannedGames = current.games,
                useOpenings = current.useOpenings,
                moveTimeMs = current.moveTimeMs,
                variant = current.variant,
                timeControlId = current.timeControl.id,
            )
            _state.update {
                it.copy(
                    starting = false,
                    error = if (id == null) "Es läuft bereits ein Turnier." else null,
                )
            }
            if (id != null) {
                // Foreground-Service hält den Prozess wach, damit das Turnier auch
                // bei ausgeschaltetem Bildschirm weiterläuft.
                TournamentService.start(getApplication())
                onStarted(id)
            }
        }
    }

    private fun defaultName(): String {
        val engineCount = _state.value.selected.size
        return trNow("Turnier: %s Engines · %s Partien", engineCount, _state.value.games)
    }
}

data class TournamentDetailState(
    val tournament: Tournament? = null,
    val games: List<TournamentGame> = emptyList(),
    val table: TournamentTable? = null,
    val run: TournamentRunState? = null,
    /** Stellung und Züge der gerade laufenden Partie (für das Live-Brett). */
    val live: TournamentLiveGame? = null,
    val loading: Boolean = true,
) {
    val progress: Float
        get() {
            val planned = tournament?.plannedGames ?: 0
            val played = tournament?.playedGames ?: 0
            return if (planned <= 0) 0f else (played.toFloat() / planned).coerceIn(0f, 1f)
        }
}

/** Auswertung eines Turniers: Tabelle, Diagramme und Partienliste. */
class TournamentDetailViewModel(app: Application, private val tournamentId: Long) :
    AndroidViewModel(app) {

    private val container = (app as App).container
    private val controller = container.tournamentController

    private val parts = combine(
        container.tournamentRepository.observe(tournamentId),
        container.tournamentRepository.observeGames(tournamentId),
        controller.state,
        controller.live,
    ) { tournament, games, run, live ->
        val table = tournament?.let { Standings.compute(it.participants, games) }
        TournamentDetailState(
            tournament = tournament,
            games = games,
            table = table,
            run = run?.takeIf { it.tournamentId == tournamentId },
            live = live?.takeIf { it.tournamentId == tournamentId },
            loading = false,
        )
    }

    val state: StateFlow<TournamentDetailState> = parts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TournamentDetailState())

    fun abort() {
        container.tournamentController.abort()
        TournamentService.stop(getApplication())
    }

    /** Setzt ein unterbrochenes Turnier mit den fehlenden Partien fort. */
    fun resume() {
        val tournament = state.value.tournament ?: return
        viewModelScope.launch {
            if (container.tournamentController.resume(tournament)) {
                TournamentService.start(getApplication())
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            if (controller.state.value?.tournamentId == tournamentId) {
                controller.abort()
                TournamentService.stop(getApplication())
            }
            container.tournamentRepository.delete(tournamentId)
            onDeleted()
        }
    }

    /** Live-Zwischenstand aus dem Controller (für Hinweise während des Laufs). */
    fun progress(): TournamentProgress? {
        val run = controller.state.value ?: return null
        if (run.tournamentId != tournamentId) return null
        return TournamentProgress(
            tournamentId = run.tournamentId,
            gameNumber = run.playedGames + 1,
            totalGames = run.totalGames,
            whiteLabel = run.whiteLabel.orEmpty(),
            blackLabel = run.blackLabel.orEmpty(),
            openingName = run.openingName,
        )
    }

}
