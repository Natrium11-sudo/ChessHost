package org.example.chesshost.chess

/**
 * Erzeugt die SAN-Notation (Standard Algebraic Notation) für einen Zug.
 * Die Schreibweise hängt am Regelwerk des Bretts (Rochade, Schachzeichen).
 */
object San {

    fun of(board: Board, move: Move, legalMoves: List<Move> = board.legalMoves()): String =
        board.rules.san(board, move, legalMoves)
}
