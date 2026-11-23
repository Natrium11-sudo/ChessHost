package org.example.chesshost.engine

import java.util.Locale

/**
 * Bewertung einer Stellung aus Weiß-Sicht. [centipawns] ist null, wenn die
 * Engine ein Matt ansagt ([mateIn], positiv = Matt für Weiß).
 */
data class EngineEval(
    val centipawns: Int?,
    val mateIn: Int?,
    val bestMoveUci: String?,
    val pv: List<String>,
    val depth: Int = 0,
) {

    /** Anteil für Weiß auf dem Bewertungsbalken (0..1). */
    val whiteShare: Float
        get() {
            val mate = mateIn
            if (mate != null) return if (mate > 0) 0.95f else 0.05f
            val cp = centipawns ?: return 0.5f
            return (0.5f + cp / 800f).coerceIn(0.05f, 0.95f)
        }

    /** Kurzform für die Anzeige: „+0.35“, „−1.20“ oder „Matt in 3“. */
    fun label(): String {
        val mate = mateIn
        if (mate != null) return if (mate > 0) "Matt in $mate" else "Matt in ${-mate}"
        val cp = centipawns ?: return "–"
        val pawns = cp / 100.0
        return String.format(Locale.ROOT, "%+.2f", pawns)
    }
}
