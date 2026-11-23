package org.example.schach.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.example.schach.chess.TimeControl
import org.example.schach.chess.TimeControls
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ein Turnier über mehrere Engines. Die Teilnehmer stehen als JSON im Datensatz,
 * damit sich Einstellungen (Schwierigkeit, Bedenkzeit, Threads, Hash) eines
 * Turniers nicht ändern, wenn der Katalog später bearbeitet wird.
 */
@Entity(tableName = "tournaments")
data class Tournament(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    val plannedGames: Int,
    val playedGames: Int,
    val useOpenings: Boolean,
    val moveTimeMs: Long,
    val participantsJson: String,
    /** Regelwerk des Turniers, z. B. "standard" oder "chess960". */
    @ColumnInfo(defaultValue = "standard")
    val variant: String = "standard",
    /**
     * Bedenkzeit der Partien (siehe `TimeControls`), z. B. "blitz_5_0".
     * "correspondence" bedeutet „ohne Uhr“ – so wurden Turniere vor der
     * Zeitklassen-Auswahl gespielt.
     */
    @ColumnInfo(defaultValue = "correspondence")
    val timeControlId: String = "correspondence",
) {

    val variantName: String
        get() = org.example.schach.chess.Rules.byId(variant).name

    val timeControl: TimeControl
        get() = TimeControls.byId(timeControlId)
    val participants: List<TournamentParticipant>
        get() = TournamentParticipant.listFromJson(participantsJson)

    val isRunning: Boolean get() = status == STATUS_RUNNING

    companion object {
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_FINISHED = "finished"
        const val STATUS_ABORTED = "aborted"
    }
}

/** Eine Partie eines Turniers inklusive PGN. */
@Entity(tableName = "tournament_games", indices = [Index("tournamentId")])
data class TournamentGame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tournamentId: Long,
    val gameNumber: Int,
    val round: Int,
    val whiteId: String,
    val blackId: String,
    val whiteLabel: String,
    val blackLabel: String,
    /** "1-0", "0-1" oder "1/2-1/2" */
    val result: String,
    val termination: String,
    val openingName: String?,
    val startFen: String?,
    val moveCount: Int,
    val pgn: String,
    val playedAt: Long,
)

/**
 * Eine teilnehmende Engine mit den Einstellungen, die für das ganze Turnier
 * gelten. [id] ist die Pack-ID; dadurch kann dieselbe Engine nicht zweimal
 * teilnehmen, was für die Auswertung wichtig ist.
 */
data class TournamentParticipant(
    val id: String,
    val label: String,
    val difficulty: String,
    val skillLevel: Int,
    val moveTimeMs: Long,
    val threads: Int,
    val hashMb: Int,
    val elo: Int?,
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("label", label)
        put("difficulty", difficulty)
        put("skillLevel", skillLevel)
        put("moveTimeMs", moveTimeMs)
        put("threads", threads)
        put("hashMb", hashMb)
        if (elo != null) put("elo", elo)
    }

    companion object {
        fun fromJson(json: JSONObject): TournamentParticipant? {
            val id = json.optString("id").trim()
            if (id.isEmpty()) return null
            return TournamentParticipant(
                id = id,
                label = json.optString("label", id).trim().ifEmpty { id },
                difficulty = json.optString("difficulty", "CLUB").trim(),
                skillLevel = json.optInt("skillLevel", 6),
                moveTimeMs = json.optLong("moveTimeMs", 500L),
                threads = json.optInt("threads", 1),
                hashMb = json.optInt("hashMb", 32),
                elo = json.optInt("elo", 0).takeIf { it > 0 },
            )
        }

        fun listToJson(participants: List<TournamentParticipant>): String =
            JSONArray().apply { participants.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(text: String): List<TournamentParticipant> = runCatching {
            val array = JSONArray(text)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { json ->
                        fromJson(json)?.let { add(it) }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
