package org.example.schach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 speichert zusätzlich das Regelwerk einer Partie. Bestehende Partien
 * bekommen "standard" – ihre PGN bleibt unverändert lesbar.
 */
@Database(entities = [SavedGame::class], version = 2, exportSchema = false)
abstract class SchachDatabase : RoomDatabase() {

    abstract fun savedGameDao(): SavedGameDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_games ADD COLUMN variant TEXT NOT NULL DEFAULT 'standard'")
            }
        }

        fun build(context: Context): SchachDatabase =
            Room.databaseBuilder(context, SchachDatabase::class.java, "schach.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
