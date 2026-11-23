package org.example.schach.engine.tournament

/** Eine angesetzte Paarung: Spielnummer ab 1, Runde ab 1. */
data class Pairing(
    val gameNumber: Int,
    val round: Int,
    val whiteId: String,
    val blackId: String,
)

/**
 * Spielplan für ein Turnier „jeder gegen jeden“.
 *
 * Ein Durchgang besteht aus allen geordneten Paaren: A–B, A–C, …, B–A, B–C, …
 * Damit spielt jede Engine gegen jede andere einmal mit Weiß und einmal mit
 * Schwarz, und die Farbverteilung ist insgesamt ausgeglichen. Für die gewünschte
 * Partienzahl wird der Durchgang so oft wiederholt, wie nötig – bei zwei Engines
 * und 100 Partien also 50-mal je Farbe.
 */
object PairingSchedule {

    fun schedule(participantIds: List<String>, games: Int): List<Pairing> {
        require(participantIds.size >= 2) { "Ein Turnier braucht mindestens zwei Engines." }
        require(games >= 1) { "Ein Turnier braucht mindestens eine Partie." }

        val cycle = buildList {
            for (white in participantIds.indices) {
                for (black in participantIds.indices) {
                    if (white != black) add(participantIds[white] to participantIds[black])
                }
            }
        }

        return (0 until games).map { index ->
            val (white, black) = cycle[index % cycle.size]
            Pairing(
                gameNumber = index + 1,
                round = index / cycle.size + 1,
                whiteId = white,
                blackId = black,
            )
        }
    }
}
