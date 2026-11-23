package org.example.schach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Eigene Datenbank für eigene Stellungen – wie bei den Turnieren bewusst
 * getrennt, damit `schach.db` unangetastet bleibt.
 */
@Database(entities = [SavedPosition::class], version = 1, exportSchema = false)
abstract class PositionDatabase : RoomDatabase() {

    abstract fun positionDao(): SavedPositionDao

    companion object {
        fun build(context: Context): PositionDatabase =
            Room.databaseBuilder(context, PositionDatabase::class.java, "positions.db").build()
    }
}
