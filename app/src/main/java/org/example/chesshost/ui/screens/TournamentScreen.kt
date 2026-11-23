package org.example.chesshost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.chesshost.App
import org.example.chesshost.chess.Color as ChessColor
import org.example.chesshost.chess.TimeControl
import org.example.chesshost.chess.formatClock
import org.example.chesshost.data.Tournament
import org.example.chesshost.data.TournamentGame
import org.example.chesshost.ui.components.ChartLegend
import org.example.chesshost.ui.components.Crosstable
import org.example.chesshost.ui.components.PointsChart
import org.example.chesshost.ui.components.ResultsChart
import org.example.chesshost.ui.components.ChessBoard
import org.example.chesshost.ui.components.MoveList
import org.example.chesshost.ui.components.SectionCard
import org.example.chesshost.ui.components.clockColor
import org.example.chesshost.ui.components.rememberCountdown
import org.example.chesshost.ui.components.sanPairs
import org.example.chesshost.ui.settings.SettingsViewModel
import org.example.chesshost.ui.tournament.Standings
import org.example.chesshost.ui.tournament.StandingRow
import org.example.chesshost.engine.Difficulty
import org.example.chesshost.engine.tournament.TournamentLiveGame
import org.example.chesshost.ui.tournament.TournamentDetailState
import org.example.chesshost.ui.tournament.TournamentDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentScreen(
    tournamentId: Long,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as App
    val viewModel: TournamentDetailViewModel = viewModel(
        key = "tournament-$tournamentId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TournamentDetailViewModel(app, tournamentId) as T
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.tournament?.name ?: "Turnier") },
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
                HeaderCard(
                    state = state,
                    onAbort = viewModel::abort,
                    onResume = viewModel::resume,
                )
            }

            state.live?.let { live ->
                item {
                    LiveBoardCard(
                        live = live,
                        running = state.run?.running == true,
                        timeControl = state.tournament?.timeControl,
                        showCoordinates = settings.showCoordinates,
                        highlightLastMove = settings.highlightLastMove,
                    )
                }
            }

            state.table?.let { table ->
                if (table.rows.any { it.games > 0 }) {
                    item { WinnerCard(state, table.rows) }
                    item { TableCard(table.rows) }
                    item {
                        SectionCard {
                            Text("Punkte", style = MaterialTheme.typography.titleSmall)
                            PointsChart(
                                rows = table.rows,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    item {
                        SectionCard {
                            Text("Siege · Remis · Niederlagen", style = MaterialTheme.typography.titleSmall)
                            ResultsChart(
                                rows = table.rows,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            ChartLegend(modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                    if (table.rows.size > 2) {
                        item {
                            SectionCard {
                                Text("Kreuztabelle", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Zeile gegen Spalte, gelesen als Siege/Remis/Niederlagen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Crosstable(table = table, modifier = Modifier.padding(top = 10.dp))
                            }
                        }
                    }
                }
            }

            state.tournament?.let { tournament ->
                item { SettingsCard(tournament) }
            }

            if (state.games.isNotEmpty()) {
                item { GamesCard(state.games) }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.delete(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Turnier löschen")
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    state: TournamentDetailState,
    onAbort: () -> Unit,
    onResume: () -> Unit,
) {
    val tournament = state.tournament
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (tournament?.status) {
                    Tournament.STATUS_RUNNING -> "Läuft"
                    Tournament.STATUS_PAUSED -> "Pausiert"
                    Tournament.STATUS_FINISHED -> "Beendet"
                    else -> "Abgebrochen"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${tournament?.playedGames ?: 0}/${tournament?.plannedGames ?: 0}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tournament != null && tournament.variant != "standard") {
            Text(
                text = tournament.variantName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        val run = state.run
        if (run != null && run.running) {
            Text(
                text = tr(
                    "Gerade: %s gegen %s",
                    run.whiteLabel.orEmpty(),
                    run.blackLabel.orEmpty(),
                ) + (run.openingName?.let { " · " + tr(it) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(onClick = onAbort, modifier = Modifier.padding(top = 10.dp)) {
                Text("Turnier abbrechen")
            }
        }
        val status = tournament?.status
        if ((status == Tournament.STATUS_PAUSED || status == Tournament.STATUS_ABORTED) &&
            run?.running != true
        ) {
            Text(
                text = if (status == Tournament.STATUS_ABORTED) {
                    "Das Turnier wurde abgebrochen. Die gespielten Partien bleiben erhalten."
                } else {
                    "Das Turnier wurde unterbrochen. Die gespielten Partien bleiben erhalten."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onResume, modifier = Modifier.padding(top = 10.dp)) {
                Text("Turnier fortsetzen")
            }
        }
        run?.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Live-Brett der gerade laufenden Partie: Stellung, letzter Zug und Zugliste. */
@Composable
private fun LiveBoardCard(
    live: TournamentLiveGame,
    running: Boolean,
    timeControl: TimeControl?,
    showCoordinates: Boolean,
    highlightLastMove: Boolean,
) {
    // Die Uhren laufen zwischen den Zügen sichtbar weiter; der Runner liefert
    // den verbindlichen Stand nach jedem Zug.
    val whiteTimeMs = rememberCountdown(
        timeMs = live.whiteTimeMs,
        running = running && live.clocked && live.sideToMove == ChessColor.WHITE,
    )
    val blackTimeMs = rememberCountdown(
        timeMs = live.blackTimeMs,
        running = running && live.clocked && live.sideToMove == ChessColor.BLACK,
    )
    SectionCard {
        Text(
            text = tr("Live-Brett · Partie %s", live.gameNumber),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = tr("%s (Weiß) gegen %s (Schwarz)", live.whiteLabel, live.blackLabel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        timeControl?.takeIf { it.clocked }?.let { control ->
            Text(
                text = tr("%s · %s", tr(control.category.label), control.notation) +
                    if (control.incrementMs > 0) {
                        tr(" · Aufschlag %s s je Zug", control.incrementMs / 1000)
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ChessBoard(
            pieces = live.pieces,
            lastMove = if (highlightLastMove) live.lastMove else null,
            checkSquare = live.checkSquare,
            showCoordinates = showCoordinates,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = if (live.sideToMove == ChessColor.WHITE) "Weiß am Zug" else "Schwarz am Zug",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (live.clocked) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = tr("Weiß %s", formatClock(whiteTimeMs)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = clockColor(
                        timeMs = whiteTimeMs,
                        active = live.sideToMove == ChessColor.WHITE,
                    ),
                )
                Text(
                    text = tr("Schwarz %s", formatClock(blackTimeMs)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = clockColor(
                        timeMs = blackTimeMs,
                        active = live.sideToMove == ChessColor.BLACK,
                    ),
                )
            }
        }
        if (live.sanMoves.isNotEmpty()) {
            MoveList(
                pairs = sanPairs(live.sanMoves),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun WinnerCard(state: TournamentDetailState, rows: List<StandingRow>) {
    val leader = rows.firstOrNull() ?: return
    val finished = state.tournament?.status == Tournament.STATUS_FINISHED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (finished) "Turniersieger" else "Führt gerade",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Text(
                text = leader.participant.label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = tr("%s von %s Punkten", formatPoints(leader.points), leader.games) +
                    " · " + tr(
                        "%s Siege, %s Remis, %s Niederlagen",
                        leader.wins,
                        leader.draws,
                        leader.losses,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            val range = leader.eloDiffRange
            if (leader.eloDiff != null && range != null) {
                Text(
                    text = tr("Geschätzte Spielstärke: %s Elo", Standings.formatElo(leader.eloDiff)) +
                        " " + tr(
                            "(%s bis %s)",
                            Standings.formatElo(range.start),
                            Standings.formatElo(range.endInclusive),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun TableCard(rows: List<StandingRow>) {
    SectionCard {
        Text("Tabelle", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp))
            Text("Engine", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("S", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp))
            Text("U", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp))
            Text("N", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp))
            Text("Pkt", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 5.dp))
        }
        rows.forEachIndexed { index, row ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.participant.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    Text(
                        text = tr(
                            "%s · %s ms · %s %%",
                            tr(Difficulty.fromName(row.participant.difficulty).label),
                            row.participant.moveTimeMs,
                            row.percent,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${row.wins}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 5.dp))
                Text("${row.draws}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 5.dp))
                Text("${row.losses}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 5.dp))
                Text(
                    text = formatPoints(row.points),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(tournament: Tournament) {
    SectionCard {
        Text("Einstellungen", style = MaterialTheme.typography.titleSmall)
        val timeControl = tournament.timeControl
        Text(
            text = (if (timeControl.clocked) {
                tr("Bedenkzeit %s · %s", tr(timeControl.category.label), timeControl.notation)
            } else {
                tr("Ohne Uhr")
            }) + " · " + tr("%s ms Denkzeit je Zug", tournament.moveTimeMs) + " · " +
                tr(if (tournament.useOpenings) "mit wechselnden Eröffnungen" else "alle Partien ab Grundstellung"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        tournament.participants.forEach { participant ->
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Text(participant.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr(
                    "Stufe %s (Skill %s) · %s",
                    tr(Difficulty.fromName(participant.difficulty).label),
                    participant.skillLevel,
                    tr("Threads %s · Hash %s MB", participant.threads, participant.hashMb),
                ) + (participant.elo?.let { " · $it Elo" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GamesCard(games: List<TournamentGame>) {
    val shown = games.takeLast(20).reversed()
    SectionCard {
        Text("Partien", style = MaterialTheme.typography.titleSmall)
        Text(
            text = if (games.size <= 20) {
                tr("%s gespielte Partien", games.size)
            } else {
                tr("Die letzten 20 von %s Partien", games.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        shown.forEach { game ->
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 5.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${game.gameNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = tr(game.whiteLabel) + " – " + tr(game.blackLabel),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                    Text(
                        text = game.openingName ?: "Grundstellung",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(game.result, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = game.termination,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatPoints(points: Double): String =
    if (points % 1.0 == 0.0) points.toInt().toString() else String.format("%.1f", points)
