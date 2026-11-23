package org.example.schach.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import org.example.schach.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.example.schach.chess.Color
import org.example.schach.chess.Move

/** Auswahl der Umwandlungsfigur, wenn ein Bauer die letzte Reihe erreicht. */
@Composable
fun PromotionDialog(
    options: List<Move>,
    color: Color,
    onSelect: (Move) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Umwandlung") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { move ->
                    val type = move.promotion ?: return@forEach
                    Image(
                        painter = painterResource(pieceDrawable(type, color)),
                        contentDescription = type.letter.toString(),
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onSelect(move) }
                            .padding(4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
