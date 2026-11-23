package org.example.schach.chess

/** Farbe einer Figur. */
enum class Color {
    WHITE,
    BLACK;

    val opposite: Color
        get() = if (this == WHITE) BLACK else WHITE
}

/** Figurentyp ohne Farbe. */
enum class PieceType {
    PAWN,
    KNIGHT,
    BISHOP,
    ROOK,
    QUEEN,
    KING;

    /** Buchstabe in FEN/SAN (immer groß). */
    val letter: Char
        get() = when (this) {
            PAWN -> 'P'
            KNIGHT -> 'N'
            BISHOP -> 'B'
            ROOK -> 'R'
            QUEEN -> 'Q'
            KING -> 'K'
        }

    val isSlider: Boolean
        get() = this == BISHOP || this == ROOK || this == QUEEN
}

data class Piece(val type: PieceType, val color: Color) {
    val fenChar: Char
        get() = if (color == Color.WHITE) type.letter else type.letter.lowercaseChar()
}

/**
 * Ein Zug in internen Koordinaten (0..63, a1 = 0, h8 = 63).
 * Rochade wird als Königszug auf sein Zielfeld dargestellt (e1g1 / e1c1);
 * [castleRook] merkt sich zusätzlich das Turmfeld, weil das bei Schach960
 * nicht ableitbar ist und Engines dort `e1h1` statt `e1g1` erwarten.
 */
data class Move(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val enPassant: Boolean = false,
    val castleRook: Int = -1,
) {
    /** UCI-Schreibweise, z. B. "e2e4" oder "e7e8q". */
    fun uci(): String = squareName(from) + squareName(to) + (promotion?.letter?.lowercase() ?: "")

    val isPromotion: Boolean
        get() = promotion != null

    val isCastle: Boolean
        get() = castleRook >= 0
}

/** Feldname in algebraischer Notation, z. B. 12 -> "e2". */
fun squareName(square: Int): String = "${'a' + square % 8}${square / 8 + 1}"

/** Feldindex aus Datei und Reihe (0-basiert). */
fun squareIndex(file: Int, rank: Int): Int = rank * 8 + file

fun fileOf(square: Int): Int = square % 8

fun rankOf(square: Int): Int = square / 8
