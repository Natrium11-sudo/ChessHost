package org.example.chesshost.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.example.chesshost.data.TournamentParticipant
import org.example.chesshost.ui.tournament.CrossCell
import org.example.chesshost.ui.tournament.StandingRow
import org.example.chesshost.ui.tournament.TournamentTable

/** Farben der drei Ausgänge – einmal definiert, damit alle Diagramme gleich aussehen. */
private object ChartColors {
    val win: Color @Composable get() = MaterialTheme.colorScheme.primary
    val draw: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val loss: Color @Composable get() = MaterialTheme.colorScheme.error
}

@Composable
fun ChartLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot("Siege", ChartColors.win)
        LegendDot("Remis", ChartColors.draw)
        LegendDot("Niederlagen", ChartColors.loss)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Waagerechtes Balkendiagramm der Punkte. Bewusst mit Layout-Gewichten statt
 * Canvas: Die Beschriftung bleibt so automatisch lesbar und skaliert mit der
 * Schriftgröße des Systems.
 */
@Composable
fun PointsChart(rows: List<StandingRow>, modifier: Modifier = Modifier) {
    val max = rows.maxOfOrNull { it.points }?.takeIf { it > 0 } ?: 1.0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}. ${tr(row.participant.label)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(124.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((row.points / max).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == 0) ChartColors.win else ChartColors.win.copy(alpha = 0.55f)),
                    )
                }
                Text(
                    text = formatPoints(row.points),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
}

/** Gestapelte Balken: Siege, Remis und Niederlagen je Engine. */
@Composable
fun ResultsChart(rows: List<StandingRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.participant.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.wins} / ${row.draws} / ${row.losses}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp)),
                ) {
                    Segment(row.wins, ChartColors.win)
                    Segment(row.draws, ChartColors.draw)
                    Segment(row.losses, ChartColors.loss)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Segment(count: Int, color: Color) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .weight(count.toFloat())
            .fillMaxHeight()
            .background(color),
    )
}

/** Kreuztabelle: Zeile gegen Spalte, Zelle = S/U/N aus Sicht der Zeile. */
@Composable
fun Crosstable(table: TournamentTable, modifier: Modifier = Modifier) {
    val participants: List<TournamentParticipant> = table.rows.map { it.participant }
    if (participants.size < 2) return

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "",
                modifier = Modifier.width(96.dp),
                style = MaterialTheme.typography.labelSmall,
            )
            participants.forEach { participant ->
                Text(
                    text = shortLabel(participant.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        participants.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = shortLabel(row.label),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
                participants.forEach { column ->
                    val cell: CrossCell? = if (row.id == column.id) {
                        null
                    } else {
                        table.crosstable[row.id to column.id]
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cellColor(cell)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (cell == null || cell.games == 0) "·" else "${cell.wins}/${cell.draws}/${cell.losses}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun cellColor(cell: CrossCell?): Color {
    val scheme = MaterialTheme.colorScheme
    if (cell == null || cell.games == 0) return scheme.surfaceVariant.copy(alpha = 0.4f)
    return when {
        cell.wins > cell.losses -> ChartColors.win.copy(alpha = 0.22f)
        cell.losses > cell.wins -> ChartColors.loss.copy(alpha = 0.22f)
        else -> ChartColors.draw.copy(alpha = 0.22f)
    }
}

private fun shortLabel(label: String): String {
    val cleaned = label.substringBefore(" · ").ifBlank { label }
    return if (cleaned.length <= 12) cleaned else cleaned.take(11) + "…"
}

private fun formatPoints(points: Double): String =
    if (points % 1.0 == 0.0) points.toInt().toString() else String.format("%.1f", points)
