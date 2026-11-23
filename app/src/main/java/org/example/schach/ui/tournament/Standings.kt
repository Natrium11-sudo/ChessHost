package org.example.schach.ui.tournament

import org.example.schach.data.TournamentGame
import org.example.schach.data.TournamentParticipant
import kotlin.math.sqrt

/** Ein Eintrag der Tabelle: eine Engine mit ihren Ergebnissen im Turnier. */
data class StandingRow(
    val participant: TournamentParticipant,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
) {
    val games: Int get() = wins + draws + losses
    val points: Double get() = wins + draws / 2.0
    /** Punkteausbeute 0..1 – der eigentliche Stärkeindikator. */
    val score: Double get() = if (games == 0) 0.0 else points / games
    val percent: Int get() = (score * 100).toInt()

    /** 95 %-Konfidenzintervall der Punkteausbeute (Wilson, n = Partien). */
    val scoreRange: ClosedFloatingPointRange<Double>
        get() = Standings.wilsonInterval(score, games)

    /** Aus der Ausbeute geschätzte Elo-Differenz zum Feld. */
    val eloDiff: Int? get() = Standings.scoreToElo(score)

    val eloDiffRange: IntRange?
        get() {
            if (games == 0) return null
            val range = scoreRange
            val low = Standings.scoreToElo(range.start) ?: return null
            val high = Standings.scoreToElo(range.endInclusive) ?: return null
            return low..high
        }
}

/** Eine Paarung im Turnier, für die Kreuztabelle aufbereitet. */
data class CrossCell(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0) {
    val games: Int get() = wins + draws + losses
}

data class TournamentTable(
    val rows: List<StandingRow>,
    val crosstable: Map<Pair<String, String>, CrossCell>,
)

/**
 * Wertet die Partien eines Turniers aus. Reine Rechenlogik ohne Android-Bezug,
 * damit sie testbar bleibt.
 */
object Standings {

    fun compute(
        participants: List<TournamentParticipant>,
        games: List<TournamentGame>,
    ): TournamentTable {
        val rows = participants.associate { it.id to StandingRow(it) }.toMutableMap()
        val cells = mutableMapOf<Pair<String, String>, CrossCell>()

        games.forEach { game ->
            val white = rows[game.whiteId]
            val black = rows[game.blackId]

            val (whitePoints, blackPoints) = when (game.result) {
                "1-0" -> 1.0 to 0.0
                "0-1" -> 0.0 to 1.0
                else -> 0.5 to 0.5
            }

            if (white != null && black != null) {
                rows[game.whiteId] = white.updated(whitePoints)
                rows[game.blackId] = black.updated(blackPoints)
            }

            cells.merge(
                game.whiteId to game.blackId,
                CrossCell(
                    wins = if (whitePoints == 1.0) 1 else 0,
                    draws = if (whitePoints == 0.5) 1 else 0,
                    losses = if (blackPoints == 1.0) 1 else 0,
                ),
            ) { a, b -> CrossCell(a.wins + b.wins, a.draws + b.draws, a.losses + b.losses) }
        }

        val sorted = rows.values.sortedWith(
            compareByDescending<StandingRow> { it.points }
                .thenByDescending { it.wins }
                .thenBy { it.participant.label },
        )
        return TournamentTable(sorted, cells)
    }

    /** Punkt für den Teilnehmer (1 / 0,5 / 0). */
    private fun StandingRow.updated(points: Double): StandingRow = when (points) {
        1.0 -> copy(wins = wins + 1)
        0.5 -> copy(draws = draws + 1)
        else -> copy(losses = losses + 1)
    }

    /** Umrechnung Punkteausbeute → Elo-Differenz (Standardformel der Elo-Zahlen). */
    fun scoreToElo(score: Double): Int? {
        if (score <= 0.0 || score >= 1.0) return null
        val elo = -400.0 * kotlin.math.log10(1.0 / score - 1.0)
        return elo.toInt()
    }

    /** Wilson-Intervall ohne Normal-Approximation – taugt auch bei wenigen Partien. */
    fun wilsonInterval(score: Double, games: Int, z: Double = 1.96): ClosedFloatingPointRange<Double> {
        if (games <= 0) return 0.0..1.0
        val p = score.coerceIn(0.0, 1.0)
        val n = games.toDouble()
        val denominator = 1 + z * z / n
        val center = (p + z * z / (2 * n)) / denominator
        val margin = z * sqrt((p * (1 - p) + z * z / (4 * n)) / n) / denominator
        return (center - margin).coerceIn(0.0, 1.0)..(center + margin).coerceIn(0.0, 1.0)
    }

    /** Nur für die Anzeige: „+124“. */
    fun formatElo(diff: Int?): String = when {
        diff == null -> "–"
        diff > 0 -> "+$diff"
        else -> diff.toString()
    }
}
