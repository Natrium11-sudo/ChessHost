package org.example.schach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import org.example.schach.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.ui.components.DifficultyDropdown
import org.example.schach.ui.components.NoEngineCard
import org.example.schach.ui.components.SectionCard
import org.example.schach.ui.components.TimeControlDropdown
import org.example.schach.ui.i18n.engineTitle
import org.example.schach.ui.i18n.tr
import org.example.schach.ui.tournament.TournamentSetupViewModel

private val GAME_COUNTS = listOf(10, 20, 30, 50, 100)
private val MOVE_TIMES = listOf(200L, 500L, 1000L, 2000L)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TournamentSetupScreen(
    onBack: () -> Unit,
    onStarted: (Long) -> Unit,
    onOpenStore: () -> Unit,
    viewModel: TournamentSetupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neues Turnier") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                SectionCard {
                    Text("Name", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        singleLine = true,
                        placeholder = { Text("Turnier am Wochenende") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            item {
                SectionCard {
                    Text("Regelwerk", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Schach960 startet jede Partie von einer anderen Grundstellung mit " +
                            "Rochade über die tatsächlichen Turmfelder. Maia spielt nur Standard-Schach " +
                            "und fehlt deshalb dort.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.variants.forEach { variant ->
                            FilterChip(
                                selected = state.variant == variant.id,
                                onClick = { viewModel.setVariant(variant.id) },
                                label = { Text(variant.name) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard {
                    Text("Engines", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Mindestens zwei auswählen. Jede Engine spielt gegen jede andere " +
                            "mit beiden Farben.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    if (state.available.isEmpty()) {
                        NoEngineCard(onOpenStore = onOpenStore)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.available.forEach { pack ->
                                FilterChip(
                                    selected = pack.id in state.selected,
                                    onClick = { viewModel.toggleEngine(pack.id) },
                                    label = { Text(engineTitle(pack.name, pack.version)) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.selected.isNotEmpty()) {
                item {
                    SectionCard {
                        Text("Schwierigkeit je Engine", style = MaterialTheme.typography.titleSmall)
                        state.selected.forEachIndexed { index, packId ->
                            val pack = state.available.firstOrNull { it.id == packId } ?: return@forEachIndexed
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = engineTitle(pack.name, pack.version),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                DifficultyDropdown(
                                    label = "Stufe",
                                    selected = state.difficulties[packId] ?: org.example.schach.engine.Difficulty.CLUB,
                                    onSelected = { viewModel.setDifficulty(packId, it) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard {
                    Text("Partien", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GAME_COUNTS.forEach { count ->
                            FilterChip(
                                selected = state.games == count,
                                onClick = { viewModel.setGames(count) },
                                label = { Text(count.toString()) },
                            )
                        }
                    }
                    Text(
                        text = estimate(state.games, state.moveTimeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                SectionCard {
                    Text("Bedenkzeit", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Bullet, Blitz, Schnellschach oder klassisch: Die Uhr gilt für " +
                            "alle Partien. Läuft sie ab, verliert die Engine. „Ohne Uhr“ ist " +
                            "Fernschach – es wird nie auf Zeit verloren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    TimeControlDropdown(
                        label = "Zeitklasse",
                        selected = state.timeControl,
                        onSelected = viewModel::setTimeControl,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "Denkzeit je Zug",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = if (state.timeControl.clocked) {
                            "Obergrenze für das Rechnen je Zug. Mit Uhr denkt eine Engine " +
                                "höchstens ein Dreißigstel ihrer Restzeit; die Denkzeit wird " +
                                "von ihrer Uhr abgezogen."
                        } else {
                            "Wie lange eine Engine je Zug rechnen darf."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        MOVE_TIMES.forEach { ms ->
                            FilterChip(
                                selected = state.moveTimeMs == ms,
                                onClick = { viewModel.setMoveTime(ms) },
                                label = { Text(tr("%s ms", ms)) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Verschiedene Startstellungen", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Ohne das spielen zwei Engines mit fester Bedenkzeit " +
                                    "immer dieselbe Partie. Mit 27 Eröffnungen wird jede Partie " +
                                    "von einer anderen Stellung gestartet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.useOpenings, onCheckedChange = viewModel::setUseOpenings)
                    }
                }
            }

            state.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            item {
                if (state.runActive) {
                    Text(
                        text = "Es läuft bereits ein Turnier. Beende oder brich es zuerst ab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { viewModel.start(onStarted) },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.starting) "Startet …" else "Turnier starten")
                }
            }
        }
    }
}

/** Grobe Laufzeitschätzung: 60 Halbzüge je Partie, beide Seiten rechnen. */
@Composable
private fun estimate(games: Int, moveTimeMs: Long): String {
    val seconds = games * 60.0 * (moveTimeMs / 1000.0)
    val minutes = (seconds / 60).toInt()
    val duration = if (minutes < 60) {
        tr("%s Minuten", minutes)
    } else {
        tr("%s Stunden %s Minuten", minutes / 60, minutes % 60)
    }
    return tr(
        "%s Partien à ca. 60 Halbzüge: ungefähr %s. Das Gerät sollte dabei am Strom bleiben.",
        games,
        duration,
    )
}
