package org.example.chesshost.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Gespeicherte Partie (PGN + Metadaten). */
@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val whiteName: String,
    val blackName: String,
    /** "vs_engine" oder "engine_vs_engine" */
    val mode: String,
    /** Ergebnistext, z. B. "1-0", "0-1", "1/2-1/2" */
    val result: String,
    val termination: String,
    val pgn: String,
    val moveCount: Int,
    val playedAt: Long,
    /** Regelwerk der Partie, z. B. "standard" oder "chess960". */
    @ColumnInfo(defaultValue = "standard")
    val variant: String = "standard",
)
