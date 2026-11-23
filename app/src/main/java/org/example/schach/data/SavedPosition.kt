package org.example.schach.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Eine im Stellungseditor gebaute Stellung. Sie ist reine Datenhaltung: die
 * Stellung selbst ist eine FEN, gespielt wird sie wie jede andere Partie.
 */
@Entity(tableName = "saved_positions")
data class SavedPosition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fen: String,
    /** Regelwerk, z. B. "standard" oder "chess960". */
    val variant: String = "standard",
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface SavedPositionDao {

    @Query("SELECT * FROM saved_positions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedPosition>>

    @Query("SELECT * FROM saved_positions WHERE id = :id")
    suspend fun find(id: Long): SavedPosition?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: SavedPosition): Long

    @Query("DELETE FROM saved_positions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_positions")
    suspend fun deleteAll()
}
