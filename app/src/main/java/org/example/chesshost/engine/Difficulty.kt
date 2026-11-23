package org.example.chesshost.engine

/**
 * Schwierigkeitsstufen. Sie werden bei Stockfish über die UCI-Option
 * "Skill Level" (0..20) plus eine Bedenkzeit pro Zug abgebildet.
 */
enum class Difficulty(val skillLevel: Int, val moveTimeMs: Long, val label: String) {
    BEGINNER(0, 120L, "Anfänger"),
    EASY(3, 250L, "Leicht"),
    CLUB(6, 450L, "Vereinsspieler"),
    ADVANCED(10, 700L, "Fortgeschritten"),
    STRONG(14, 1100L, "Stark"),
    MASTER(18, 1700L, "Meister"),
    MAXIMUM(20, 2500L, "Maximal");

    companion object {
        fun fromName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: CLUB
    }
}

/**
 * Parameter für eine einzelne Engine-Suche. [variant] ist die ID des
 * Regelwerks (`standard`, `chess960`) und entscheidet über die UCI-Option der
 * Engine sowie über die FEN-Schreibweise.
 */
data class EngineConfig(
    val skillLevel: Int,
    val moveTimeMs: Long,
    val variant: String = "standard",
)

fun Difficulty.toConfig(variant: String = "standard"): EngineConfig =
    EngineConfig(skillLevel = skillLevel, moveTimeMs = moveTimeMs, variant = variant)
