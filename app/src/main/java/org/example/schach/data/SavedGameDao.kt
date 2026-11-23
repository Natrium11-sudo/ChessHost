package org.example.schach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {

    @Query("SELECT * FROM saved_games ORDER BY playedAt DESC")
    fun observeAll(): Flow<List<SavedGame>>

    @Query("SELECT * FROM saved_games WHERE id = :id")
    suspend fun findById(id: Long): SavedGame?

    @Insert
    suspend fun insert(game: SavedGame): Long

    @Query("DELETE FROM saved_games WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_games")
    suspend fun deleteAll()
}
