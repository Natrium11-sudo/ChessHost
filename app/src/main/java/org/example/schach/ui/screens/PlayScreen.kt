package org.example.schach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import org.example.schach.ui.i18n.Text
import org.example.schach.ui.i18n.tr
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.chess.Rules
import org.example.schach.chess.formatClock
import org.example.schach.ui.components.ChessBoard
import org.example.schach.ui.components.DifficultyDropdown
import org.example.schach.ui.components.EngineDropdown
import org.example.schach.ui.components.MoveFeedback
import org.example.schach.ui.components.MoveList
import org.example.schach.ui.components.NoEngineCard
import org.example.schach.ui.components.PromotionDialog
import org.example.schach.ui.components.ResultBanner
import org.example.schach.ui.components.SectionCard
import org.example.schach.ui.components.EvalBar
import org.example.schach.ui.components.TimeControlDropdown
import org.example.schach.ui.components.clockColor
import org.example.schach.ui.components.rememberCountdown
import org.example.schach.ui.components.sanPairs
import org.example.schach.ui.play.PlayUiState
import org.example.schach.ui.play.PlayViewModel
import org.example.schach.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    startSpec: String? = null,
    viewModel: PlayViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    LaunchedEffect(startSpec) { viewModel.applyStartSpec(startSpec) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spiel gegen Engine") },
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
            if (engines.isEmpty()) {
                NoEngineCard(onOpenStore)
            } else {
                EngineCard(
                    state = state,
                    engines = engines,
                    onDifficulty = viewModel::setDifficulty,
                    onEngine = viewModel::setEngine,
                )
            }

            StartPositionCard(
                state = state,
                onVariant = viewModel::setVariant,
                onStandard = viewModel::startStandard,
                onRandom960 = viewModel::newChess960Game,
                onTimeControl = viewModel::setTimeControl,
            )

            if (state.timeControl.clocked) {
                ClockRow(state)
            }

            AnalysisCard(state = state, onAnalyse = viewModel::analyse)

            ChessBoard(
                pieces = state.pieces,
                selected = state.selected,
                legalTargets = if (settings.showLegalMoves) state.legalTargets else emptySet(),
                lastMove = if (settings.highlightLastMove) state.lastMove else null,
                checkSquare = state.checkSquare,
                showCoordinates = settings.showCoordinates,
                onSquareClick = viewModel::onSquareClick,
            )

            MoveFeedback(
                moveCount = state.sanMoves.size,
                check = state.checkSquare != null,
                gameOver = state.end != null,
                sound = settings.soundEnabled,
                haptics = settings.hapticsEnabled,
            )

            StatusRow(state)

            if (state.halfmoveClock > 0 && state.end == null) {
                Text(
                    text = tr("50-Züge-Regel: %s von 100 Halbzügen", state.halfmoveClock),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.end?.let { ResultBanner(it) }

            state.engineError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = viewModel::newGame, modifier = Modifier.weight(1f)) {
                    Text("Neue Partie")
                }
                OutlinedButton(
                    onClick = viewModel::undo,
                    enabled = state.sanMoves.isNotEmpty() && !state.thinking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Zug zurück")
                }
                OutlinedButton(
                    onClick = viewModel::resign,
                    enabled = state.end == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Aufgeben")
                }
            }

            OutlinedButton(
                onClick = viewModel::offerDraw,
                enabled = state.end == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remis anbieten")
            }

            SectionCard {
                Text("Züge", style = MaterialTheme.typography.titleSmall)
                MoveList(pairs = sanPairs(state.sanMoves))
            }
        }
    }

    if (state.promotionOptions.isNotEmpty()) {
        PromotionDialog(
            options = state.promotionOptions,
            color = state.playerColor,
            onSelect = viewModel::choosePromotion,
            onDismiss = viewModel::cancelPromotion,
        )
    }
}

/** Bewertung, Tipp und Hauptvariante zur aktuellen Stellung. */
@Composable
private fun AnalysisCard(state: PlayUiState, onAnalyse: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Analyse",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onAnalyse,
                enabled = !state.analysing && !state.thinking,
            ) {
                Text(if (state.analysing) "Rechnet …" else "Bewerten")
            }
        }
        val evaluation = state.evaluation
        when {
            state.analysing -> Text(
                text = "Engine rechnet …",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            evaluation == null -> Text(
                text = "Bewertet die Stellung und zeigt den besten Zug.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                EvalBar(evaluation, modifier = Modifier.padding(top = 4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(evaluation.label(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = tr("Tiefe %s", evaluation.depth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.hintSan?.let {
                        Text(tr("Tipp: %s", it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.hintLine.isNotEmpty()) {
                    Text(
                        text = tr("Beste Variante: %s", state.hintLine.joinToString(" ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPositionCard(
    state: PlayUiState,
    onVariant: (String) -> Unit,
    onStandard: () -> Unit,
    onRandom960: () -> Unit,
    onTimeControl: (String) -> Unit,
) {
    val editable = !state.thinking
    SectionCard {
        Text("Startstellung", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Regelwerk, Startstellung und Bedenkzeit – eine Änderung beginnt eine neue Partie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
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
        state.startFenLabel?.let { fen ->
            Text(
                text = fen,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            if (state.variantId == "chess960") {
                OutlinedButton(onClick = onRandom960, enabled = editable) {
                    Text("Zufällige 960-Stellung")
                }
            }
            if (state.startFenLabel != null) {
                TextButton(onClick = onStandard, enabled = editable) { Text("Standardstellung") }
            }
        }
    }
}

/** Zwei Uhren nebeneinander; die Seite am Zug ist hervorgehoben. */
@Composable
private fun ClockRow(state: PlayUiState) {
    val playerIsWhite = state.playerColor == org.example.schach.chess.Color.WHITE
    // Die Uhren laufen sichtbar zwischen den Zügen; der Zustand liefert nur den
    // verbindlichen Stand nach jedem Zug.
    val playerTimeMs = rememberCountdown(
        timeMs = if (playerIsWhite) state.whiteTimeMs else state.blackTimeMs,
        running = state.end == null && state.sideToMove == state.playerColor,
    )
    val engineTimeMs = rememberCountdown(
        timeMs = if (playerIsWhite) state.blackTimeMs else state.whiteTimeMs,
        running = state.end == null && state.thinking,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ClockBox(
            label = if (playerIsWhite) "Du · Weiß" else "Du · Schwarz",
            timeMs = playerTimeMs,
            active = state.end == null && state.sideToMove == state.playerColor,
            modifier = Modifier.weight(1f),
        )
        ClockBox(
            label = tr(
                "%s · %s",
                state.engineName.ifBlank { tr("Engine") },
                tr(if (playerIsWhite) "Schwarz" else "Weiß"),
            ),
            timeMs = engineTimeMs,
            active = state.end == null && state.sideToMove != state.playerColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClockBox(
    label: String,
    timeMs: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = formatClock(timeMs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = clockColor(timeMs, active),
            )
        }
    }
}

@Composable
private fun EngineCard(
    state: PlayUiState,
    engines: List<org.example.schach.engine.packs.EnginePack>,
    onDifficulty: (org.example.schach.engine.Difficulty) -> Unit,
    onEngine: (String) -> Unit,
) {
    val editable = !state.thinking && state.end == null
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gegner", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            EngineDropdown(
                label = "Engine",
                engines = engines,
                selectedId = state.engineId,
                onSelected = onEngine,
                enabled = editable,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = state.engineName.ifBlank { "Engine" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            DifficultyDropdown(
                label = "Schwierigkeit",
                selected = state.difficulty,
                onSelected = onDifficulty,
                enabled = editable,
            )
        }
    }
}

@Composable
private fun StatusRow(state: PlayUiState) {
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
                state.thinking -> tr("%s überlegt …", state.engineName)
                else -> "Du bist am Zug"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
