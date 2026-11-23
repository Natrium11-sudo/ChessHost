package org.example.schach.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import org.example.schach.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.App
import org.example.schach.chess.Color
import org.example.schach.chess.Piece
import org.example.schach.chess.PieceType
import org.example.schach.ui.components.ChessBoard
import org.example.schach.ui.components.SectionCard
import org.example.schach.ui.components.pieceDrawable
import org.example.schach.ui.positions.EditorState
import org.example.schach.ui.positions.PositionEditorViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PositionEditorScreen(
    positionId: Long,
    onBack: () -> Unit,
    onPlay: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PositionEditorViewModel = viewModel(
        key = "position-editor-$positionId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PositionEditorViewModel(context.applicationContext as App, positionId) as T
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fenDraft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.savedId == null) "Stellung bauen" else "Stellung bearbeiten") },
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
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name der Stellung") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.variants.forEach { variant ->
                    FilterChip(
                        selected = state.variantId == variant.id,
                        onClick = { viewModel.setVariant(variant.id) },
                        label = { Text(variant.name) },
                    )
                }
            }

            ChessBoard(
                pieces = state.pieces,
                onSquareClick = viewModel::place,
            )

            SectionCard {
                Text("Figuren", style = MaterialTheme.typography.titleSmall)
                PiecePalette(
                    selected = state.palette,
                    erasing = state.erasing,
                    onSelect = viewModel::selectPiece,
                    onErase = viewModel::selectEraser,
                )
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Am Zug", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = state.sideToMove == Color.WHITE,
                        onClick = { if (state.sideToMove != Color.WHITE) viewModel.toggleSideToMove() },
                        label = { Text("Weiß") },
                    )
                    FilterChip(
                        selected = state.sideToMove == Color.BLACK,
                        onClick = { if (state.sideToMove != Color.BLACK) viewModel.toggleSideToMove() },
                        label = { Text("Schwarz") },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Rochade", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    CastlingChip(state, viewModel, Color.WHITE, kingSide = true, label = "Weiß kurz")
                    CastlingChip(state, viewModel, Color.WHITE, kingSide = false, label = "lang")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text("", modifier = Modifier.weight(1f))
                    CastlingChip(state, viewModel, Color.BLACK, kingSide = true, label = "Schwarz kurz")
                    CastlingChip(state, viewModel, Color.BLACK, kingSide = false, label = "lang")
                }
            }

            state.problems.firstOrNull()?.let { problem ->
                Text(
                    text = problem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            SectionCard {
                Text("FEN", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = state.fen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = fenDraft,
                    onValueChange = { fenDraft = it },
                    label = { Text("FEN einfügen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = { viewModel.setFen(fenDraft) }) { Text("Übernehmen") }
                    TextButton(onClick = viewModel::reset) { Text("Grundstellung") }
                    TextButton(onClick = viewModel::clear) { Text("Leeren") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onPlay(state.fen, state.variantId) },
                    enabled = state.canPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Spielen")
                }
                OutlinedButton(onClick = { viewModel.save { } }) {
                    Text("Speichern")
                }
                if (state.rules.id == "chess960") {
                    OutlinedButton(onClick = viewModel::randomChess960) {
                        Text("Zufall")
                    }
                }
            }
            Text(
                text = "Tippen setzt die gewählte Figur, der Radierer entfernt sie. " +
                    "Ein unbesetztes Feld lässt sich mit dem Radierer leeren.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun CastlingChip(
    state: EditorState,
    viewModel: PositionEditorViewModel,
    color: Color,
    kingSide: Boolean,
    label: String,
) {
    FilterChip(
        selected = state.castling.has(color, kingSide),
        onClick = { viewModel.toggleCastling(color, kingSide) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PiecePalette(
    selected: Piece?,
    erasing: Boolean,
    onSelect: (Piece) -> Unit,
    onErase: () -> Unit,
) {
    val pieces = buildList {
        for (color in listOf(Color.WHITE, Color.BLACK)) {
            for (type in PieceType.entries) add(Piece(type, color))
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        pieces.forEach { piece ->
            val isSelected = !erasing && selected == piece
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        else Modifier,
                    )
                    .clickable { onSelect(piece) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(pieceDrawable(piece)),
                    contentDescription = piece.type.letter.toString(),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (erasing) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                )
                .clickable(onClick = onErase),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Backspace, contentDescription = "Radierer")
        }
    }
}
