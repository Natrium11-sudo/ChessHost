package org.example.schach.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.chess.Board
import org.example.schach.chess.Pgn
import org.example.schach.chess.Rules
import org.example.schach.ui.components.ChessBoard
import org.example.schach.ui.components.MoveList
import org.example.schach.ui.components.SectionCard
import org.example.schach.ui.components.sanPairs
import org.example.schach.ui.history.HistoryViewModel
import org.example.schach.ui.i18n.Text
import org.example.schach.ui.i18n.tr
import org.example.schach.ui.i18n.trNow
import org.example.schach.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: Long,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val game by viewModel.selected.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gameId) { viewModel.load(gameId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partie") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Zurück"))
                    }
                },
                actions = {
                    val pgn = game?.pgn
                    if (pgn != null) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, pgn)
                            }
                            context.startActivity(Intent.createChooser(intent, "PGN"))
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = tr("PGN teilen"))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        val current = game
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text("Partie wird geladen …", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        // Die Partie wird einmal ausgewertet; der Regler läuft danach durch die Stellungen.
        val replay = remember(current.pgn, current.variant) {
            Pgn.replay(current.pgn, Rules.byId(current.variant))
        }
        var index by remember(current.pgn) { mutableIntStateOf(replay.steps.lastIndex) }
        val safeIndex = index.coerceIn(0, replay.steps.lastIndex)
        val step = replay.steps[safeIndex]
        val board = remember(step.fen) {
            runCatching { Board.fromFen(step.fen, replay.rules) }.getOrNull()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text("${current.whiteName} – ${current.blackName}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = tr(
                        "%s · %s · %s Züge",
                        current.result,
                        tr(current.termination),
                        current.moveCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard {
                ChessBoard(
                    pieces = board?.snapshot().orEmpty(),
                    lastMove = if (settings.highlightLastMove) step.lastMove else null,
                    checkSquare = board?.let {
                        if (it.isInCheck(it.sideToMove)) it.kingSquare(it.sideToMove) else null
                    },
                    showCoordinates = settings.showCoordinates,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { index = 0 }, enabled = safeIndex > 0) {
                        Text("|<")
                    }
                    TextButton(onClick = { index = safeIndex - 1 }, enabled = safeIndex > 0) {
                        Text("<")
                    }
                    Text(
                        text = tr("Zug %s von %s", safeIndex, replay.moveCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { index = safeIndex + 1 },
                        enabled = safeIndex < replay.steps.lastIndex,
                    ) {
                        Text(">")
                    }
                    TextButton(
                        onClick = { index = replay.steps.lastIndex },
                        enabled = safeIndex < replay.steps.lastIndex,
                    ) {
                        Text(">|")
                    }
                }
                step.san?.let {
                    Text(
                        text = tr("Letzter Zug: %s", it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (replay.moveCount > 0) {
                SectionCard {
                    Text("Züge", style = MaterialTheme.typography.titleSmall)
                    MoveList(pairs = sanPairs(replay.sanMoves()))
                }
            }

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PGN",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(current.pgn))
                        scope.launch { snackbar.showSnackbar(trNow("PGN kopiert")) }
                    }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = tr("PGN kopieren"),
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        text = current.pgn,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
