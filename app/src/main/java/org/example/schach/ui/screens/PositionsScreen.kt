package org.example.schach.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import org.example.schach.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.App
import org.example.schach.chess.Rules
import org.example.schach.data.SavedPosition
import org.example.schach.ui.components.SectionCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eigene Stellungen: Liste, Einstieg in den Editor und Spielstart. */
class PositionsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as App).container.positionRepository

    val positions: StateFlow<List<SavedPosition>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PositionsScreen(
    onBack: () -> Unit,
    onNewPosition: () -> Unit,
    onEdit: (Long) -> Unit,
    onPlay: (String, String) -> Unit,
    onNewChess960: () -> Unit,
    onMatch: (String, String) -> Unit,
    viewModel: PositionsViewModel = viewModel(),
) {
    val positions by viewModel.positions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onNewPosition) {
                        Icon(Icons.Rounded.Add, contentDescription = "Neue Stellung")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Text("Von einer Stellung spielen", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Baue im Editor eine Stellung, speichere sie und starte von dort eine Partie " +
                            "gegen eine Engine – oder ein Duell zweier Engines. Gespeichert wird eine FEN, " +
                            "alles andere läuft wie gewohnt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = onNewPosition) { Text("Editor") }
                        OutlinedButton(onClick = onNewChess960) { Text("Schach960") }
                    }
                }
            }

            if (positions.isEmpty()) {
                item {
                    Text(
                        text = "Noch keine eigenen Stellungen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(positions, key = { it.id }) { position ->
                PositionCard(
                    position = position,
                    onPlay = { onPlay(position.fen, position.variant) },
                    onMatch = { onMatch(position.fen, position.variant) },
                    onEdit = { onEdit(position.id) },
                    onDelete = { viewModel.delete(position.id) },
                )
            }
        }
    }
}

@Composable
private fun PositionCard(
    position: SavedPosition,
    onPlay: () -> Unit,
    onMatch: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(position.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = Rules.byId(position.variant).name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Bearbeiten")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Löschen")
            }
        }
        Text(
            text = position.fen,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Text("Gegen Engine")
            }
            OutlinedButton(onClick = onMatch, modifier = Modifier.weight(1f)) {
                Text("Engine-Duell")
            }
        }
    }
}
