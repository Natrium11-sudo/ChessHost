package org.example.schach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Tournament>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    fun observe(id: Long): Flow<Tournament?>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun find(id: Long): Tournament?

    @Query("SELECT * FROM tournaments WHERE status = 'running' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findRunning(): Tournament?

    @Insert
    suspend fun insert(tournament: Tournament): Long

    @Query("UPDATE tournaments SET playedGames = :playedGames WHERE id = :id")
    suspend fun updatePlayed(id: Long, playedGames: Int)

    @Query(
        "UPDATE tournaments SET status = :status, finishedAt = :finishedAt WHERE id = :id",
    )
    suspend fun updateStatus(id: Long, status: String, finishedAt: Long?)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tournament_games WHERE tournamentId = :tournamentId ORDER BY gameNumber ASC")
    fun observeGames(tournamentId: Long): Flow<List<TournamentGame>>

    @Query("SELECT * FROM tournament_games WHERE tournamentId = :tournamentId ORDER BY gameNumber ASC")
    suspend fun games(tournamentId: Long): List<TournamentGame>

    @Insert
    suspend fun insertGame(game: TournamentGame): Long

    @Query("DELETE FROM tournament_games WHERE tournamentId = :tournamentId")
    suspend fun deleteGames(tournamentId: Long)
}
