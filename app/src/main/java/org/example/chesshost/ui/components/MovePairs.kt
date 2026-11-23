package org.example.chesshost.ui.components

/** Wandelt eine SAN-Liste in "Nummer + Weiß/Schwarz"-Paare für die Anzeige um. */
fun sanPairs(sanMoves: List<String>): List<Pair<Int, Pair<String?, String?>>> {
    if (sanMoves.isEmpty()) return emptyList()
    val result = ArrayList<Pair<Int, Pair<String?, String?>>>((sanMoves.size + 1) / 2)
    var index = 0
    var number = 1
    while (index < sanMoves.size) {
        val white = sanMoves.getOrNull(index)
        val black = sanMoves.getOrNull(index + 1)
        result.add(number to (white to black))
        index += 2
        number++
    }
    return result
}
