package org.example.schach.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Zugriff auf Turniere und ihre Partien. */
class TournamentRepository(private val dao: TournamentDao) {

    fun observeAll(): Flow<List<Tournament>> = dao.observeAll()

    /** Turniere, deren Auswertung angezeigt werden kann (mit mindestens einer Partie). */
    fun observeFinished(): Flow<List<Tournament>> =
        dao.observeAll().map { list -> list.filter { !it.isRunning && it.playedGames > 0 } }

    fun observe(id: Long): Flow<Tournament?> = dao.observe(id)

    fun observeGames(id: Long): Flow<List<TournamentGame>> = dao.observeGames(id)

    suspend fun find(id: Long): Tournament? = dao.find(id)

    suspend fun games(id: Long): List<TournamentGame> = dao.games(id)

    /** Läuft schon ein Turnier? Es darf immer nur eines gleichzeitig laufen. */
    suspend fun running(): Tournament? = dao.findRunning()

    suspend fun create(
        name: String,
        participants: List<TournamentParticipant>,
        plannedGames: Int,
        useOpenings: Boolean,
        moveTimeMs: Long,
        variant: String = "standard",
        timeControlId: String = "correspondence",
    ): Long = dao.insert(
        Tournament(
            name = name,
            createdAt = System.currentTimeMillis(),
            status = Tournament.STATUS_RUNNING,
            plannedGames = plannedGames,
            playedGames = 0,
            useOpenings = useOpenings,
            moveTimeMs = moveTimeMs,
            participantsJson = TournamentParticipant.listToJson(participants),
            variant = variant,
            timeControlId = timeControlId,
        ),
    )

    suspend fun addGame(game: TournamentGame, playedGames: Int) {
        dao.insertGame(game)
        dao.updatePlayed(game.tournamentId, playedGames)
    }

    suspend fun finish(id: Long, status: String) {
        dao.updateStatus(
            id = id,
            status = status,
            finishedAt = System.currentTimeMillis(),
        )
    }

    /** Setzt ein pausiertes Turnier wieder auf „läuft“ (ohne Endzeitpunkt). */
    suspend fun markRunning(id: Long) {
        dao.updateStatus(id = id, status = Tournament.STATUS_RUNNING, finishedAt = null)
    }

    suspend fun delete(id: Long) {
        dao.deleteGames(id)
        dao.delete(id)
    }
}
