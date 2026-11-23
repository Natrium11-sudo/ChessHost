package org.example.chesshost.ui.components

import androidx.annotation.DrawableRes
import org.example.chesshost.R
import org.example.chesshost.chess.Color
import org.example.chesshost.chess.Piece
import org.example.chesshost.chess.PieceType

/** Grafik (Cburnett-Set, CC BY-SA 3.0) für eine Figur. */
@DrawableRes
fun pieceDrawable(piece: Piece): Int = when (piece.color) {
    Color.WHITE -> when (piece.type) {
        PieceType.KING -> R.drawable.piece_klt45
        PieceType.QUEEN -> R.drawable.piece_qlt45
        PieceType.ROOK -> R.drawable.piece_rlt45
        PieceType.BISHOP -> R.drawable.piece_blt45
        PieceType.KNIGHT -> R.drawable.piece_nlt45
        PieceType.PAWN -> R.drawable.piece_plt45
    }
    Color.BLACK -> when (piece.type) {
        PieceType.KING -> R.drawable.piece_kdt45
        PieceType.QUEEN -> R.drawable.piece_qdt45
        PieceType.ROOK -> R.drawable.piece_rdt45
        PieceType.BISHOP -> R.drawable.piece_bdt45
        PieceType.KNIGHT -> R.drawable.piece_ndt45
        PieceType.PAWN -> R.drawable.piece_pdt45
    }
}

@DrawableRes
fun pieceDrawable(type: PieceType, color: Color): Int = pieceDrawable(Piece(type, color))
