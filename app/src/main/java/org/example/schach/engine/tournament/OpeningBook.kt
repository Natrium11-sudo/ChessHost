package org.example.schach.engine.tournament

import android.content.Context
import org.json.JSONObject

/** Eine Startstellung mit Namen, z. B. „Sizilianisch, Najdorf“. */
data class Opening(val name: String, val fen: String)

/**
 * Eröffnungsstellungen, damit nicht alle Partien eines Turniers identisch
 * verlaufen. Zwei Engines mit fester Bedenkzeit sind deterministisch – ohne
 * verschiedene Startstellungen würde 100-mal dieselbe Partie gespielt.
 *
 * Der Schalter im Turnier-Setup entscheidet, ob gemischt wird; ohne Eröffnungen
 * starten alle Partien von der Grundstellung.
 */
class OpeningBook(private val openings: List<Opening>) {

    val size: Int get() = openings.size

    /** Startstellung für die n-te Partie (1-basiert), oder null = Grundstellung. */
    fun forGame(gameNumber: Int): Opening? {
        if (openings.isEmpty()) return null
        return openings[(gameNumber - 1).coerceAtLeast(0) % openings.size]
    }

    companion object {
        private const val ASSET = "openings/standard.json"

        /**
         * Startstellungen für Schach960. Das Standard-Eröffnungsbuch ist dort
         * unbrauchbar, weil Rochaderechte und Grundstellung andere sind.
         */
        fun chess960(seed: Long, count: Int = 24): OpeningBook {
            val random = kotlin.random.Random(seed)
            val openings = buildList {
                add(
                    Opening(
                        "Schach960 · Grundstellung",
                        org.example.schach.chess.Board.initial(org.example.schach.chess.Chess960Rules).fen(),
                    ),
                )
                for (index in 1 until count) {
                    val fen = org.example.schach.chess.Chess960Rules.randomStartFen(
                        kotlin.random.Random(seed * 31 + index),
                    )
                    add(Opening("Schach960 #$index", fen))
                }
            }
            return OpeningBook(openings)
        }

        fun fromAssets(context: Context): OpeningBook = runCatching {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val array = root.optJSONArray("openings") ?: return@runCatching OpeningBook(emptyList())
            val list = buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val name = json.optString("name").trim()
                    val fen = json.optString("fen").trim()
                    if (name.isNotEmpty() && fen.isNotEmpty()) add(Opening(name, fen))
                }
            }
            OpeningBook(list)
        }.getOrElse { OpeningBook(emptyList()) }
    }
}
