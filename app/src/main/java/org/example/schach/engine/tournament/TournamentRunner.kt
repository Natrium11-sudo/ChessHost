package org.example.schach.engine.tournament

import android.util.Log
import org.example.schach.chess.Board
import org.example.schach.chess.ChessGame
import org.example.schach.chess.Color
import org.example.schach.chess.GameResult
import org.example.schach.chess.Move
import org.example.schach.chess.Piece
import org.example.schach.chess.Rules
import org.example.schach.chess.TimeControls
import org.example.schach.data.Tournament
import org.example.schach.data.TournamentGame
import org.example.schach.data.TournamentParticipant
import org.example.schach.engine.ChessEngine
import org.example.schach.engine.EngineConfig
import org.example.schach.ui.i18n.trNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Meldung an die Oberfläche während ein Turnier läuft. */
data class TournamentProgress(
    val tournamentId: Long,
    val gameNumber: Int,
    val totalGames: Int,
    val whiteLabel: String,
    val blackLabel: String,
    val openingName: String?,
)

/**
 * Stand der gerade laufenden Partie – Grundlage für das Live-Brett im
 * Turnier-Screen. Wird nach jedem Zug ausgestoßen; die Felder sind für die
 * Anzeige fertig aufbereitet, damit die Oberfläche nichts nachrechnen muss.
 */
data class TournamentLiveGame(
    val tournamentId: Long,
    val gameNumber: Int,
    val whiteLabel: String,
    val blackLabel: String,
    val variant: String,
    val startFen: String,
    val fen: String,
    val pieces: List<Piece?>,
    val sideToMove: Color,
    val lastMove: Move?,
    val checkSquare: Int?,
    val sanMoves: List<String>,
    /** Restzeiten beider Seiten in ms; ohne Uhr bleiben sie auf 0. */
    val whiteTimeMs: Long = 0,
    val blackTimeMs: Long = 0,
    val clocked: Boolean = false,
)

/**
 * Spielt ein Turnier vollständig durch: Pairings abarbeiten, je Partie eine
 * Startstellung wählen, Züge von beiden Engines holen und das Ergebnis
 * speichern.
 *
 * Die Engine-Instanzen werden **einmal** erzeugt und über alle Partien
 * wiederverwendet. Das ist nicht nur schneller (Stockfish lädt sonst für jede
 * Partie 78 MB Netze nach), sondern hält auch die Hash-Tabelle warm.
 */
class TournamentRunner(
    /**
     * Liefert eine bereits gestartete Engine für Teilnehmer und Regelwerk;
     * wirft, wenn sie nicht startbar ist.
     */
    private val openEngine: suspend (TournamentParticipant, String) -> ChessEngine,
) {

    suspend fun run(
        tournament: Tournament,
        openingBook: OpeningBook,
        /** Partien bis einschließlich dieser Nummer gelten als gespielt (Fortsetzen). */
        startAfterGame: Int = 0,
        onProgress: suspend (TournamentProgress) -> Unit,
        onGame: suspend (TournamentGame) -> Unit,
        onLive: suspend (TournamentLiveGame) -> Unit = {},
    ) {
        val participants = tournament.participants
        require(participants.size >= 2) { "Ein Turnier braucht mindestens zwei Engines." }
        val rules = Rules.byId(tournament.variant)

        val engines = LinkedHashMap<String, ChessEngine>()
        try {
            participants.forEach { participant ->
                engines[participant.id] = openEngine(participant, rules.id)
            }

            val pairings = PairingSchedule.schedule(
                participantIds = participants.map { it.id },
                games = tournament.plannedGames,
            )
            val byId = participants.associateBy { it.id }

            for (pairing in pairings) {
                if (pairing.gameNumber <= startAfterGame) continue
                coroutineContext.ensureActive()
                val white = byId.getValue(pairing.whiteId)
                val black = byId.getValue(pairing.blackId)
                val opening = if (tournament.useOpenings) openingBook.forGame(pairing.gameNumber) else null

                onProgress(
                    TournamentProgress(
                        tournamentId = tournament.id,
                        gameNumber = pairing.gameNumber,
                        totalGames = tournament.plannedGames,
                        whiteLabel = white.label,
                        blackLabel = black.label,
                        openingName = opening?.name,
                    ),
                )

                val record = playGame(
                    tournament = tournament,
                    pairing = pairing,
                    white = white,
                    black = black,
                    engines = engines,
                    startFen = opening?.fen ?: Board.initial(rules).fen(),
                    openingName = opening?.name,
                    onLive = onLive,
                )
                onGame(record)
            }
        } finally {
            engines.values.forEach { engine -> runCatching { engine.close() } }
        }
    }

    private suspend fun playGame(
        tournament: Tournament,
        pairing: Pairing,
        white: TournamentParticipant,
        black: TournamentParticipant,
        engines: Map<String, ChessEngine>,
        startFen: String,
        openingName: String?,
        onLive: suspend (TournamentLiveGame) -> Unit,
    ): TournamentGame {
        val rules = Rules.byId(tournament.variant)
        val timeControl = TimeControls.byId(tournament.timeControlId)
        val game = ChessGame(startFen, rules)
        var whiteTimeMs = timeControl.initialMs
        var blackTimeMs = timeControl.initialMs
        var failure: String? = null

        suspend fun publishLive() {
            val board = game.board
            onLive(
                TournamentLiveGame(
                    tournamentId = tournament.id,
                    gameNumber = pairing.gameNumber,
                    whiteLabel = white.label,
                    blackLabel = black.label,
                    variant = rules.id,
                    startFen = startFen,
                    fen = board.fen(),
                    pieces = board.snapshot(),
                    sideToMove = board.sideToMove,
                    lastMove = game.moves.lastOrNull(),
                    checkSquare = if (board.isInCheck(board.sideToMove)) {
                        board.kingSquare(board.sideToMove).takeIf { it >= 0 }
                    } else {
                        null
                    },
                    sanMoves = game.sanMoves.toList(),
                    whiteTimeMs = whiteTimeMs,
                    blackTimeMs = blackTimeMs,
                    clocked = timeControl.clocked,
                ),
            )
        }

        publishLive()
        var plies = 0
        while (game.end == null && plies < MAX_PLIES) {
            coroutineContext.ensureActive()
            val side = game.board.sideToMove
            val participant = if (side == Color.WHITE) white else black
            val engine = engines[participant.id]

            val remainingBefore = if (side == Color.WHITE) whiteTimeMs else blackTimeMs
            if (timeControl.clocked && remainingBefore <= 0L) {
                game.timeout(side)
                break
            }
            // Mit Uhr denkt die Engine höchstens ein Dreißigstel ihrer Restzeit;
            // die Denkzeit je Zug ist dabei die Obergrenze.
            val budget = if (timeControl.clocked) {
                minOf(participant.moveTimeMs, (remainingBefore / 30).coerceAtLeast(MIN_MOVE_TIME_MS))
            } else {
                participant.moveTimeMs
            }
            val startedAt = System.nanoTime()

            val uci = if (engine == null) {
                failure = "Engine-Fehler"
                null
            } else {
                try {
                    engine.bestMove(
                        startFen = startFen,
                        uciHistory = game.uciHistory(),
                        currentFen = game.board.fen(),
                        config = EngineConfig(
                            skillLevel = participant.skillLevel,
                            moveTimeMs = budget,
                            variant = rules.id,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    Log.w(TAG, "${participant.label} lieferte einen Fehler", t)
                    failure = "Engine-Fehler"
                    null
                }
            }

            // Die tatsächliche Denkzeit geht von der Uhr ab, danach der Aufschlag.
            if (timeControl.clocked) {
                val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0)
                val remainingAfter = (remainingBefore - elapsedMs + timeControl.incrementMs)
                    .coerceAtLeast(0)
                if (side == Color.WHITE) whiteTimeMs = remainingAfter else blackTimeMs = remainingAfter
                if (remainingAfter <= 0L) {
                    game.timeout(side)
                    break
                }
            }

            if (uci == null) {
                if (failure == null) failure = "Engine-Fehler"
                game.resign(side)
                break
            }

            val move = game.parseEngineMove(uci)
            if (move == null) {
                // Eine Engine, die einen illegalen Zug schickt, verliert die Partie.
                Log.w(TAG, "${participant.label} schickte den illegalen Zug $uci")
                failure = "Ungültiger Zug"
                game.resign(side)
                break
            }

            game.makeMove(move)
            plies++
            publishLive()
        }

        if (game.end == null) {
            game.agreeDraw()
            failure = trNow("Zuglimit (%s Halbzüge)", MAX_PLIES)
        }

        val end = game.end
        val result = end?.result?.pgn ?: GameResult.DRAW.pgn
        val termination = failure ?: end?.reason?.label ?: "Unbekannt"
        val pgn = game.toPgn(
            whiteName = white.label,
            blackName = black.label,
            extraTags = buildMap {
                put("Event", "Turnier: ${tournament.name}")
                put("Round", pairing.round.toString())
                put("Mode", "Turnier")
                if (openingName != null) put("Opening", openingName)
                if (timeControl.clocked) {
                    put(
                        "TimeControl",
                        "${timeControl.initialMs / 1000}+${timeControl.incrementMs / 1000}",
                    )
                }
            },
        )

        return TournamentGame(
            tournamentId = tournament.id,
            gameNumber = pairing.gameNumber,
            round = pairing.round,
            whiteId = white.id,
            blackId = black.id,
            whiteLabel = white.label,
            blackLabel = black.label,
            result = result,
            termination = termination,
            openingName = openingName,
            startFen = startFen.takeIf { it != Board.initial(rules).fen() },
            moveCount = game.moves.size,
            pgn = pgn,
            playedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "TournamentRunner"

        /** Abbruch nach 200 Zügen – dann gilt die Partie als Remis. */
        const val MAX_PLIES = 400

        /** Untergrenze für die Denkzeit je Zug, damit die Engine nicht in 0 ms denkt. */
        private const val MIN_MOVE_TIME_MS = 50L
    }
}
