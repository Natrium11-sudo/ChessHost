package org.example.schach.engine.tournament

import android.content.Context
import org.example.schach.chess.Rules
import org.example.schach.data.Tournament
import org.example.schach.data.TournamentParticipant
import org.example.schach.data.TournamentRepository
import org.example.schach.engine.ChessEngine
import org.example.schach.engine.EngineRegistry
import org.example.schach.ui.i18n.trNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Zustand des laufenden Turniers – Grundlage für Fortschritt und Benachrichtigung. */
data class TournamentRunState(
    val tournamentId: Long,
    val totalGames: Int,
    val playedGames: Int = 0,
    val whiteLabel: String? = null,
    val blackLabel: String? = null,
    val openingName: String? = null,
    val error: String? = null,
    val running: Boolean = true,
)

/**
 * Führt genau ein Turnier gleichzeitig aus und lebt so lange wie die App
 * (Application-Scope), nicht so lange wie ein Screen. Damit läuft ein Turnier
 * weiter, wenn die Aktivität neu aufgebaut wird; den Prozess am Leben hält der
 * Foreground-Service (siehe `service/TournamentService`).
 */
class TournamentController(
    context: Context,
    private val repository: TournamentRepository,
    engineRegistry: EngineRegistry,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = engineRegistry
    private val standardBook = OpeningBook.fromAssets(appContext)
    private val runner = TournamentRunner { participant, variant ->
        startEngine(participant, variant)
    }

    private val _state = MutableStateFlow<TournamentRunState?>(null)
    val state: StateFlow<TournamentRunState?> = _state.asStateFlow()

    /**
     * Live-Brett der gerade gespielten Partie. Absichtlich getrennt vom
     * Fortschritt: Diese Werte ändern sich mit jedem Zug, der Fortschritt nur
     * je Partie – so muss die Benachrichtigung nicht bei jedem Zug neu bauen.
     */
    private val _live = MutableStateFlow<TournamentLiveGame?>(null)
    val live: StateFlow<TournamentLiveGame?> = _live.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Legt das Turnier an und startet es. Gibt null zurück, wenn schon eines
     * läuft (es darf immer nur eines gleichzeitig laufen).
     */
    suspend fun start(
        name: String,
        participants: List<TournamentParticipant>,
        plannedGames: Int,
        useOpenings: Boolean,
        moveTimeMs: Long,
        variant: String = "standard",
        timeControlId: String = "correspondence",
    ): Long? {
        if (isRunning) return null

        val id = repository.create(
            name = name,
            participants = participants,
            plannedGames = plannedGames,
            useOpenings = useOpenings,
            moveTimeMs = moveTimeMs,
            variant = variant,
            timeControlId = timeControlId,
        )

        _live.value = null
        _state.value = TournamentRunState(tournamentId = id, totalGames = plannedGames)
        job = scope.launch {
            val tournament = repository.find(id) ?: return@launch
            runTournament(tournament)
        }
        return id
    }

    /**
     * Setzt ein unterbrochenes Turnier fort: Bereits gespielte Partien bleiben
     * erhalten, gespielt werden nur die fehlenden Paarungen.
     */
    suspend fun resume(tournament: Tournament): Boolean {
        if (isRunning) return false
        if (tournament.status == Tournament.STATUS_FINISHED) return false
        repository.markRunning(tournament.id)
        _live.value = null
        _state.value = TournamentRunState(
            tournamentId = tournament.id,
            totalGames = tournament.plannedGames,
            playedGames = tournament.playedGames,
        )
        job = scope.launch { runTournament(tournament) }
        return true
    }

    private suspend fun runTournament(tournament: Tournament) {
        val id = tournament.id
        try {
            val book = if (tournament.variant == "chess960") {
                OpeningBook.chess960(tournament.id)
            } else {
                standardBook
            }
            runner.run(
                tournament = tournament,
                openingBook = book,
                startAfterGame = tournament.playedGames,
                onProgress = { progress ->
                    _state.update { current ->
                        current?.copy(
                            playedGames = progress.gameNumber - 1,
                            whiteLabel = progress.whiteLabel,
                            blackLabel = progress.blackLabel,
                            openingName = progress.openingName,
                        )
                    }
                },
                onGame = { game -> repository.addGame(game, game.gameNumber) },
                onLive = { game -> _live.value = game },
            )
            _live.value = null
            repository.finish(id, Tournament.STATUS_FINISHED)
            _state.update { it?.copy(playedGames = tournament.plannedGames, running = false) }
        } catch (cancelled: CancellationException) {
            // Kein Datenbankzugriff hier: Der Job ist bereits abgebrochen, jeder
            // suspendierende Aufruf (auch Room) wirft sofort wieder. Den Status
            // schreibt deshalb [abort] in einem nicht abbrechbaren Scope.
            _live.value = null
            throw cancelled
        } catch (t: Throwable) {
            _live.value = null
            repository.finish(id, Tournament.STATUS_ABORTED)
            _state.update {
                it?.copy(
                    running = false,
                    whiteLabel = null,
                    blackLabel = null,
                    error = t.message ?: "Turnier abgebrochen.",
                )
            }
        }
    }

    private suspend fun startEngine(participant: TournamentParticipant, variant: String): ChessEngine {
        val pack = registry.find(participant.id)?.takeIf { registry.isSupported(it) }
            ?: error(
                trNow(
                    "%s ist nicht verfügbar (im Store aktivieren oder installieren).",
                    participant.label,
                ),
            )
        if (!pack.supportsVariant(variant)) {
            error(trNow("%s kann %s nicht spielen.", participant.label, Rules.byId(variant).name))
        }
        val engine = registry.create(pack)
            ?: error(trNow("%s konnte nicht erzeugt werden.", participant.label))
        if (!engine.start()) {
            runCatching { engine.close() }
            error(trNow("%s startet auf diesem Gerät nicht.", participant.label))
        }
        return engine
    }

    /**
     * Bricht das laufende Turnier ab. Der Zustand wird sofort auf „läuft nicht“
     * gesetzt, damit die Oberfläche reagiert; der Status in der Datenbank wird
     * erst geschrieben, wenn der abgebrochene Job wirklich beendet ist.
     */
    fun abort() {
        val cancelled = job
        val id = _state.value?.tournamentId
        job = null
        _live.value = null
        _state.update { it?.copy(running = false, whiteLabel = null, blackLabel = null) }
        cancelled?.cancel()
        if (id == null) return

        scope.launch(NonCancellable) {
            cancelled?.join()
            val state = _state.value
            // Wurde zwischenzeitlich fortgesetzt, gehört der Lauf nicht mehr zu
            // diesem Abbruch – dann bleibt der neue Status stehen.
            if (state?.tournamentId == id && state.running == true) return@launch
            // Nur ein wirklich laufendes Turnier wird auf „abgebrochen“ gesetzt;
            // ein inzwischen beendetes bleibt „beendet“.
            val stored = repository.find(id) ?: return@launch
            if (stored.status == Tournament.STATUS_RUNNING) {
                repository.finish(id, Tournament.STATUS_ABORTED)
            }
        }
    }

    /**
     * Beim App-Start: Ein „laufendes“ Turnier ohne Prozess ist unterbrochen und
     * wird pausiert, damit es später fortgesetzt werden kann.
     */
    suspend fun recoverInterrupted() {
        if (isRunning) return
        val running = repository.running() ?: return
        if (_state.value?.tournamentId == running.id) return
        repository.finish(running.id, Tournament.STATUS_PAUSED)
    }
}
