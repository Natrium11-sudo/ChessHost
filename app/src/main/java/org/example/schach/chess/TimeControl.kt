package org.example.schach.chess

/**
 * Zeitklasse einer Partie. Die Einteilung folgt den üblichen Bezeichnungen:
 * Bullet, Blitz, Schnellschach (Rapid), klassisch und Fernschach.
 */
enum class TimeCategory(val label: String, val hint: String) {
    BULLET("Bullet", "unter 3 Minuten"),
    BLITZ("Blitz", "3 bis 5 Minuten"),
    RAPID("Schnellschach", "10 bis 15 Minuten"),
    CLASSICAL("Klassisch", "ab 30 Minuten"),
    CORRESPONDENCE("Fernschach", "ohne Uhr"),
}

/**
 * Bedenkzeit einer Partie: Startzeit je Seite plus Aufschlag je Zug.
 * [initialMs] = 0 bedeutet „ohne Uhr“ – wie bei einer Fernschach-Partie ohne
 * festes Zeitlimit, es wird dann nie auf Zeit verloren.
 */
data class TimeControl(
    val id: String,
    val category: TimeCategory,
    val initialMs: Long,
    val incrementMs: Long = 0L,
) {
    /** Ohne Startzeit läuft keine Uhr und es gibt keinen Zeitablauf. */
    val clocked: Boolean
        get() = initialMs > 0

    /** Übliche Kurzform: Minuten + Aufschlag in Sekunden, z. B. „5 + 3“. */
    val notation: String
        get() = if (!clocked) "ohne Uhr" else "${initialMs / 60_000} + ${incrementMs / 1000}"

    val label: String
        get() = if (!clocked) "Ohne Uhr" else notation
}

/** Alle auswählbaren Bedenkzeiten, nach Zeitklasse sortiert. */
object TimeControls {

    val all: List<TimeControl> = listOf(
        // Bullet
        TimeControl("bullet_1_0", TimeCategory.BULLET, 60_000),
        TimeControl("bullet_2_1", TimeCategory.BULLET, 120_000, 1_000),
        // Blitz
        TimeControl("blitz_3_0", TimeCategory.BLITZ, 180_000),
        TimeControl("blitz_3_2", TimeCategory.BLITZ, 180_000, 2_000),
        TimeControl("blitz_5_0", TimeCategory.BLITZ, 300_000),
        TimeControl("blitz_5_3", TimeCategory.BLITZ, 300_000, 3_000),
        // Schnellschach
        TimeControl("rapid_10_0", TimeCategory.RAPID, 600_000),
        TimeControl("rapid_10_5", TimeCategory.RAPID, 600_000, 5_000),
        TimeControl("rapid_15_10", TimeCategory.RAPID, 900_000, 10_000),
        // Klassisch
        TimeControl("classical_30_0", TimeCategory.CLASSICAL, 1_800_000),
        TimeControl("classical_30_20", TimeCategory.CLASSICAL, 1_800_000, 20_000),
        TimeControl("classical_60_30", TimeCategory.CLASSICAL, 3_600_000, 30_000),
        // Fernschach
        TimeControl("correspondence", TimeCategory.CORRESPONDENCE, 0L),
    )

    val default: TimeControl = all.first { it.id == "blitz_5_0" }

    fun byId(id: String?): TimeControl = all.firstOrNull { it.id == id } ?: default

    fun of(category: TimeCategory): List<TimeControl> = all.filter { it.category == category }
}

/**
 * Uhrzeit für die Anzeige: „4:07“, ab einer Stunde „1:02:07“. Unter zehn
 * Sekunden werden Zehntel gezeigt („0:09.4“), damit die Uhr sichtbar abläuft.
 */
fun formatClock(ms: Long): String {
    val remaining = ms.coerceAtLeast(0)
    val totalSeconds = remaining / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        remaining in 1..9_999 -> "%d:%02d.%d".format(minutes, seconds, (remaining % 1000) / 100)
        else -> "%d:%02d".format(minutes, seconds)
    }
}
