package org.example.schach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Eigene Datenbank für Turniere. Absicht: `schach.db` (gespeicherte Partien)
 * bleibt getrennt – ein Turnier ist ein eigenes Feature, kein Umbau der
 * bestehenden Tabellen.
 *
 * Version 2 ergänzt das Regelwerk (`variant`), damit ein Turnier auch
 * Schach960 spielen kann. Die Migration füllt bestehende Turniere mit
 * "standard", es gehen keine Daten verloren.
 *
 * Version 3 ergänzt die Bedenkzeit (`timeControlId`). Bestehende Turniere
 * bekommen "correspondence" (ohne Uhr) – so wurden sie tatsächlich gespielt.
 */
@Database(
    entities = [Tournament::class, TournamentGame::class],
    version = 3,
    exportSchema = false,
)
abstract class TournamentDatabase : RoomDatabase() {

    abstract fun tournamentDao(): TournamentDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tournaments ADD COLUMN variant TEXT NOT NULL DEFAULT 'standard'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE tournaments ADD COLUMN timeControlId TEXT NOT NULL " +
                        "DEFAULT 'correspondence'",
                )
            }
        }

        fun build(context: Context): TournamentDatabase =
            Room.databaseBuilder(context, TournamentDatabase::class.java, "tournaments.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
