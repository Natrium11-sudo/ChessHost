package org.example.chesshost.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import org.example.chesshost.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.chesshost.chess.Move
import org.example.chesshost.chess.Piece
import org.example.chesshost.ui.theme.BoardDark
import org.example.chesshost.ui.theme.BoardDarkText
import org.example.chesshost.ui.theme.BoardLight
import org.example.chesshost.ui.theme.BoardLightText
import org.example.chesshost.ui.theme.CheckHighlight
import org.example.chesshost.ui.theme.LastMoveHighlight
import org.example.chesshost.ui.theme.LegalTargetColor
import org.example.chesshost.ui.theme.SelectedHighlight

/**
 * Schachbrett aus 64 Feldern. [pieces] ist die Feldliste (Index 0 = a1).
 * Alle Markierungen sind optional.
 */
@Composable
fun ChessBoard(
    pieces: List<Piece?>,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    selected: Int? = null,
    legalTargets: Set<Int> = emptySet(),
    lastMove: Move? = null,
    checkSquare: Int? = null,
    showCoordinates: Boolean = true,
    onSquareClick: (Int) -> Unit = {},
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        for (row in 0 until 8) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 8) {
                    val file = if (flipped) 7 - col else col
                    val rank = if (flipped) row else 7 - row
                    val square = rank * 8 + file
                    BoardSquare(
                        square = square,
                        file = file,
                        rank = rank,
                        piece = pieces.getOrNull(square),
                        isLight = (file + rank) % 2 == 1,
                        isSelected = selected == square,
                        isTarget = square in legalTargets,
                        isLastMove = lastMove != null && (lastMove.from == square || lastMove.to == square),
                        isCheck = checkSquare == square,
                        showFile = showCoordinates && row == 7,
                        showRank = showCoordinates && col == 0,
                        onClick = { onSquareClick(square) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardSquare(
    square: Int,
    file: Int,
    rank: Int,
    piece: Piece?,
    isLight: Boolean,
    isSelected: Boolean,
    isTarget: Boolean,
    isLastMove: Boolean,
    isCheck: Boolean,
    showFile: Boolean,
    showRank: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isLight) BoardLight else BoardDark
    val coordinateColor = if (isLight) BoardLightText else BoardDarkText

    Box(modifier = modifier.background(background).clickable(onClick = onClick)) {
        if (isLastMove) {
            Box(Modifier.matchParentSize().background(LastMoveHighlight))
        }
        if (isSelected) {
            Box(Modifier.matchParentSize().background(SelectedHighlight))
        }
        if (isCheck) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxSize(0.86f)
                    .background(CheckHighlight, CircleShape),
            )
        }
        piece?.let { current ->
            Image(
                painter = painterResource(pieceDrawable(current)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(1.dp),
            )
        }
        if (isTarget) {
            if (piece == null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.26f)
                        .background(LegalTargetColor, CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(2.dp)
                        .border(3.dp, LegalTargetColor, CircleShape),
                )
            }
        }
        if (showRank) {
            Text(
                text = "${rank + 1}",
                modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 1.dp),
                color = coordinateColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showFile) {
            Text(
                text = "${'a' + file}",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp),
                color = coordinateColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
