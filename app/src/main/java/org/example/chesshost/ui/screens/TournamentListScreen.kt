package org.example.chesshost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.chesshost.data.Tournament
import org.example.chesshost.engine.tournament.TournamentRunState
import org.example.chesshost.ui.components.SectionCard
import org.example.chesshost.ui.tournament.TournamentListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TournamentListScreen(
    onBack: () -> Unit,
    onNewTournament: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: TournamentListViewModel = viewModel(),
) {
    val tournaments by viewModel.tournaments.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Turniere") },
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
                Button(onClick = onNewTournament, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Neues Turnier", modifier = Modifier.padding(start = 8.dp))
                }
            }

            run?.takeIf { it.running }?.let { state ->
                item { RunningCard(state, onOpen = { onOpen(state.tournamentId) }) }
            }

            if (tournaments.isEmpty()) {
                item { EmptyCard() }
            } else {
                items(tournaments, key = { it.id }) { tournament ->
                    TournamentCard(tournament = tournament, onClick = { onOpen(tournament.id) })
                }
            }
        }
    }
}

@Composable
private fun RunningCard(state: TournamentRunState, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Turnier läuft",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = tr("%s von %s Partien", state.playedGames, state.totalGames),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            state.whiteLabel?.let { white ->
                Text(
                    text = tr("%s gegen %s", white, state.blackLabel.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            LinearProgressIndicator(
                progress = { if (state.totalGames == 0) 0f else state.playedGames.toFloat() / state.totalGames },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun TournamentCard(tournament: Tournament, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tournament.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = statusLabel(tournament),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = tr(
                    "%s/%s Partien · %s Engines · %s",
                    tournament.playedGames,
                    tournament.plannedGames,
                    tournament.participants.size,
                    formatDate(tournament.createdAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    SectionCard {
        Text("Noch kein Turnier", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Wähle zwei oder mehr Engines, lege die Partienzahl fest und lasse sie " +
                "gegeneinander spielen. Die App zeigt danach Tabelle, Diagramme und Kreuztabelle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun statusLabel(tournament: Tournament): String = when (tournament.status) {
    Tournament.STATUS_RUNNING -> "läuft"
    Tournament.STATUS_PAUSED -> "pausiert"
    Tournament.STATUS_FINISHED -> "beendet"
    else -> "abgebrochen"
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(timestamp))
