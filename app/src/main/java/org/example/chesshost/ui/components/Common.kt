package org.example.chesshost.ui.components

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.chesshost.chess.GameEnd
import org.example.chesshost.chess.TimeCategory
import org.example.chesshost.chess.TimeControl
import org.example.chesshost.chess.TimeControls
import org.example.chesshost.engine.Difficulty
import org.example.chesshost.engine.EngineEval
import org.example.chesshost.engine.packs.EnginePack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), content = { content() })
    }
}

@Composable
fun ResultBanner(end: GameEnd, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = end.result.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = tr("durch %s", tr(end.reason.label)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/** Kompakter Dropdown-Button für Schwierigkeitsstufen. */
@Composable
fun DifficultyDropdown(
    label: String,
    selected: Difficulty,
    onSelected: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = selected.label, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Difficulty.entries.forEach { difficulty ->
                DropdownMenuItem(
                    text = { Text(difficulty.label) },
                    onClick = {
                        expanded = false
                        onSelected(difficulty)
                    },
                )
            }
        }
    }
}

/** Bedenkzeit-Auswahl: Zeitklassen mit ihren üblichen Stufen. */
@Composable
fun TimeControlDropdown(
    label: String,
    selected: TimeControl,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = tr("%s · %s", tr(selected.category.label), selected.label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TimeCategory.entries.forEach { category ->
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                )
                TimeControls.of(category).forEach { control ->
                    DropdownMenuItem(
                        text = { Text(control.label) },
                        onClick = {
                            expanded = false
                            onSelected(control.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Hinweis, solange keine Engine aktiviert ist. Ab Werk bringt die App keine
 * spielbare Engine mit – die Auswahl passiert im Store.
 */
@Composable
fun NoEngineCard(onOpenStore: () -> Unit) {
    SectionCard {
        Text(
            text = "Keine Engine aktiv",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Diese App bringt ab Werk keine Engine mit. Wähle im Store eine Engine aus, " +
                "danach kannst du hier gegen sie spielen. Alternativ lässt sich ein LLM-Gegner " +
                "über eine API in den Einstellungen einrichten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onOpenStore,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Text("Engines wählen")
        }
    }
}

/** Auswahl einer Engine aus dem Katalog. */
@Composable
fun EngineDropdown(
    label: String,
    engines: List<EnginePack>,
    selectedId: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = engines.firstOrNull { it.id == selectedId } ?: engines.firstOrNull()
    Box(modifier = modifier) {
        TextButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
            Column(Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = selected?.title ?: "Standard",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(engine.title)
                            if (engine.tags.isNotEmpty()) {
                                Text(
                                    text = engine.tags.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(engine.id)
                    },
                )
            }
        }
    }
}

/**
 * Restzeit für eine Anzeige, die sichtbar herunterläuft. [timeMs] ist der
 * verbindliche Stand (etwa nach einem Zug); solange [running] gilt, zählt die
 * Anzeige dazwischen selbst weiter. Ändert sich [timeMs], beginnt sie neu bei
 * diesem Wert.
 */
@Composable
fun rememberCountdown(timeMs: Long, running: Boolean, tickMs: Long = 100L): Long {
    var remaining by remember { mutableLongStateOf(timeMs) }
    LaunchedEffect(timeMs, running) {
        remaining = timeMs
        if (!running) return@LaunchedEffect
        val startedAt = SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            remaining = (timeMs - elapsed).coerceAtLeast(0L)
            delay(tickMs)
        }
    }
    return if (running) remaining else timeMs
}

/** Bewertungsbalken: links Weiß, rechts Schwarz. */
@Composable
fun EvalBar(evaluation: EngineEval, modifier: Modifier = Modifier) {
    val whiteShare = evaluation.whiteShare
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2E2E2E)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(whiteShare)
                .fillMaxHeight()
                .background(Color(0xFFF2F2F2)),
        )
    }
}

/** Farbe einer Uhr: rot bei knapper Zeit, sonst hervorgehoben bzw. gedämpft. */
@Composable
fun clockColor(timeMs: Long, active: Boolean): Color = when {
    timeMs < 30_000L -> MaterialTheme.colorScheme.error
    active -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
