package org.example.chesshost.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.chesshost.chess.Color as ChessColor
import org.example.chesshost.chess.Rules
import org.example.chesshost.chess.formatClock
import org.example.chesshost.engine.Difficulty
import org.example.chesshost.ui.components.ChessBoard
import org.example.chesshost.ui.components.DifficultyDropdown
import org.example.chesshost.ui.components.EngineDropdown
import org.example.chesshost.ui.components.MoveList
import org.example.chesshost.ui.components.NoEngineCard
import org.example.chesshost.ui.components.ResultBanner
import org.example.chesshost.ui.components.SectionCard
import org.example.chesshost.ui.components.TimeControlDropdown
import org.example.chesshost.ui.components.clockColor
import org.example.chesshost.ui.components.rememberCountdown
import org.example.chesshost.ui.components.sanPairs
import org.example.chesshost.ui.match.EngineMatchUiState
import org.example.chesshost.ui.match.EngineMatchViewModel
import org.example.chesshost.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EngineMatchScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    startSpec: String? = null,
    viewModel: EngineMatchViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(startSpec) { viewModel.applyStartSpec(startSpec) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engine gegen Engine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val engines = viewModel.engines()
            StartCard(
                state = state,
                editable = !state.running && state.end == null,
                onVariant = { id -> viewModel.setStart(null, id) },
                onStartPosition = { fen, variant -> viewModel.setStart(fen, variant) },
                onRandom960 = viewModel::newChess960Start,
                onTimeControl = viewModel::setTimeControl,
            )
            val supported = engines.filter { it.supportsVariant(state.variantId) }
            if (engines.isEmpty()) {
                NoEngineCard(onOpenStore)
            } else if (supported.isEmpty()) {
                SectionCard {
                    Text(
                        text = tr(
                            "Keine der aktiven Engines kann %s spielen. Wähle im Store eine andere Engine oder ein anderes Regelwerk.",
                            state.variantName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                EngineSetupCard(
                    state = state,
                    engines = supported,
                    editable = !state.running && state.end == null,
                    onWhiteEngine = viewModel::setWhiteEngine,
                    onBlackEngine = viewModel::setBlackEngine,
                    onWhiteDifficulty = viewModel::setWhiteDifficulty,
                    onBlackDifficulty = viewModel::setBlackDifficulty,
                )
            }

            ControlRow(
                state = state,
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onAbort = viewModel::abort,
                onNewGame = viewModel::newGame,
            )

            ChessBoard(
                pieces = state.pieces,
                lastMove = if (settings.highlightLastMove) state.lastMove else null,
                checkSquare = state.checkSquare,
                showCoordinates = settings.showCoordinates,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.thinking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = when {
                        state.end != null -> "Partie beendet"
                        state.paused -> "Pausiert"
                        state.running -> tr(
                            "%s ist am Zug",
                            tr(if (state.sideToMove == ChessColor.WHITE) "Weiß" else "Schwarz"),
                        )
                        else -> "Bereit – Start drücken"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.end?.let { ResultBanner(it) }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionCard {
                Text("Züge", style = MaterialTheme.typography.titleSmall)
                MoveList(pairs = sanPairs(state.sanMoves))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(
    state: EngineMatchUiState,
    editable: Boolean,
    onVariant: (String) -> Unit,
    onStartPosition: (String?, String) -> Unit,
    onRandom960: () -> Unit,
    onTimeControl: (String) -> Unit,
) {
    val positions = state.positions.filter { it.variant == state.variantId }
    SectionCard {
        Text("Regelwerk und Startstellung", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Das Duell kann von der Grundstellung, von einer eigenen gespeicherten Stellung " +
                "oder als Schach960 laufen. Beide Engines werden auf das Regelwerk eingestellt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rules.all.forEach { variant ->
                FilterChip(
                    selected = state.variantId == variant.id,
                    onClick = { onVariant(variant.id) },
                    enabled = editable,
                    label = { Text(variant.name) },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "Bedenkzeit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TimeControlDropdown(
                label = "Zeitklasse",
                selected = state.timeControl,
                onSelected = onTimeControl,
                enabled = editable,
            )
        }

        Text(
            text = state.startFenLabel ?: "Grundstellung",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (state.variantId == Rules.chess960.id) {
                OutlinedButton(onClick = onRandom960, enabled = editable) {
                    Text("Zufällige 960-Stellung")
                }
            }
            if (state.startFenLabel != null) {
                OutlinedButton(onClick = { onStartPosition(null, state.variantId) }, enabled = editable) {
                    Text("Grundstellung")
                }
            }
        }

        if (positions.isNotEmpty()) {
            Text(
                text = "Eigene Stellungen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                positions.forEach { position ->
                    FilterChip(
                        selected = state.startFenLabel == position.fen,
                        onClick = { onStartPosition(position.fen, position.variant) },
                        enabled = editable,
                        label = { Text(position.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineSetupCard(
    state: EngineMatchUiState,
    engines: List<org.example.chesshost.engine.packs.EnginePack>,
    editable: Boolean,
    onWhiteEngine: (String) -> Unit,
    onBlackEngine: (String) -> Unit,
    onWhiteDifficulty: (Difficulty) -> Unit,
    onBlackDifficulty: (Difficulty) -> Unit,
) {
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EngineColumn(
                title = "Weiß",
                engines = engines,
                engineId = state.whiteEngineId,
                name = state.whiteEngine,
                difficulty = state.whiteDifficulty,
                timeMs = state.whiteTimeMs,
                clocked = state.timeControl.clocked,
                active = state.running && state.sideToMove == ChessColor.WHITE,
                editable = editable,
                onEngine = onWhiteEngine,
                onDifficulty = onWhiteDifficulty,
                modifier = Modifier.weight(1f),
            )
            EngineColumn(
                title = "Schwarz",
                engines = engines,
                engineId = state.blackEngineId,
                name = state.blackEngine,
                difficulty = state.blackDifficulty,
                timeMs = state.blackTimeMs,
                clocked = state.timeControl.clocked,
                active = state.running && state.sideToMove == ChessColor.BLACK,
                editable = editable,
                onEngine = onBlackEngine,
                onDifficulty = onBlackDifficulty,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EngineColumn(
    title: String,
    engines: List<org.example.chesshost.engine.packs.EnginePack>,
    engineId: String?,
    name: String,
    difficulty: Difficulty,
    timeMs: Long,
    clocked: Boolean,
    active: Boolean,
    editable: Boolean,
    onEngine: (String) -> Unit,
    onDifficulty: (Difficulty) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
            )
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EngineDropdown(
            label = "Engine",
            engines = engines,
            selectedId = engineId,
            onSelected = onEngine,
            enabled = editable,
        )
        Text(
            text = name.ifBlank { "Engine" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        DifficultyDropdown(label = "Stufe", selected = difficulty, onSelected = onDifficulty, enabled = editable)
        if (clocked) {
            // Die Uhr der Seite am Zug läuft sichtbar zwischen den Zügen weiter.
            val remaining = rememberCountdown(timeMs, running = active)
            Text(
                text = formatClock(remaining),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = clockColor(remaining, active),
            )
        }
    }
}

@Composable
private fun ControlRow(
    state: EngineMatchUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit,
    onNewGame: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when {
            state.end != null -> {
                FilledTonalButton(onClick = onNewGame, modifier = Modifier.weight(1f)) {
                    Text("Neue Partie")
                }
            }
            state.running -> {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
                OutlinedButton(onClick = onAbort, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
            }
            state.paused -> {
                FilledTonalButton(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Weiter") }
                OutlinedButton(onClick = onAbort, modifier = Modifier.weight(1f)) { Text("Abbrechen") }
            }
            else -> {
                FilledTonalButton(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start") }
            }
        }
    }
}
