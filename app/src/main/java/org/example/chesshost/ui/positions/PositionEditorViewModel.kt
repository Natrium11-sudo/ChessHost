package org.example.chesshost.ui.positions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.chesshost.App
import org.example.chesshost.chess.Board
import org.example.chesshost.chess.Castling
import org.example.chesshost.chess.Chess960Rules
import org.example.chesshost.chess.Color
import org.example.chesshost.chess.Piece
import org.example.chesshost.chess.PieceType
import org.example.chesshost.chess.RuleSet
import org.example.chesshost.chess.Rules
import org.example.chesshost.chess.fileOf
import org.example.chesshost.chess.rankOf
import org.example.chesshost.data.SavedPosition
import org.example.chesshost.ui.i18n.trNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Zustand des Stellungseditors. */
data class EditorState(
    val name: String = "",
    val variantId: String = "standard",
    val pieces: List<Piece?> = Board.initial().snapshot(),
    val sideToMove: Color = Color.WHITE,
    val castling: Castling = Castling.standard(),
    val palette: Piece? = Piece(PieceType.KNIGHT, Color.WHITE),
    val erasing: Boolean = false,
    val fen: String = Board.START_FEN,
    val problems: List<String> = emptyList(),
    val savedId: Long? = null,
    val message: String? = null,
    val variants: List<RuleSet> = Rules.all,
) {
    val rules: RuleSet get() = Rules.byId(variantId)

    val canPlay: Boolean get() = problems.isEmpty()
}

/**
 * Baut eine Stellung Feld für Feld zusammen und prüft sie fortlaufend. Die
 * Stellung selbst bleibt eine FEN – gespielt wird sie über denselben Weg wie
 * jede andere Partie.
 */
class PositionEditorViewModel(
    app: Application,
    private val positionId: Long,
) : AndroidViewModel(app) {

    private val repository = (app as App).container.positionRepository

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        if (positionId != 0L) {
            viewModelScope.launch {
                val saved = repository.find(positionId)
                if (saved == null) {
                    _state.update { it.copy(message = "Stellung nicht gefunden.") }
                } else {
                    val board = runCatching { Board.fromFen(saved.fen, Rules.byId(saved.variant)) }.getOrNull()
                    _state.update { current ->
                        val next = current.copy(
                            name = saved.name,
                            variantId = saved.variant,
                            savedId = saved.id,
                        )
                        if (board == null) next.copy(problems = listOf("Gespeicherte FEN ist ungültig.")) else next.fromBoard(board)
                    }
                }
            }
        } else {
            _state.update { it.fromBoard(Board.initial()) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun selectPiece(piece: Piece?) = _state.update { it.copy(palette = piece, erasing = false) }

    fun selectEraser() = _state.update { it.copy(erasing = true) }

    /** Setzt die gewählte Figur auf ein Feld (bzw. entfernt sie im Radiermodus). */
    fun place(square: Int) {
        _state.update { current ->
            val next = current.pieces.toMutableList()
            next[square] = if (current.erasing) null else current.palette
            current.copy(pieces = next).normalized()
        }
    }

    fun toggleSideToMove() = _state.update {
        it.copy(sideToMove = it.sideToMove.opposite).normalized()
    }

    fun setVariant(variantId: String) = _state.update { current ->
        current.copy(variantId = variantId, message = null).normalized()
    }

    fun toggleCastling(color: Color, kingSide: Boolean) {
        _state.update { current ->
            if (current.castling.has(color, kingSide)) {
                current.copy(castling = current.castling.withoutRight(color, kingSide)).normalized()
            } else {
                val board = current.toBoard()
                val king = board.kingSquare(color)
                if (king < 0) {
                    return@update current.copy(
                        message = trNow(
                            "Für die Rochade fehlt der %s König.",
                            trNow(if (color == Color.WHITE) "weiße" else "schwarze"),
                        ),
                    )
                }
                val rook = outermostRook(board, color, king, kingSide)
                if (rook < 0) {
                    return@update current.copy(
                        message = "Auf dieser Seite steht kein Turm.",
                    )
                }
                current.copy(
                    castling = current.castling.withRight(color, kingSide, rook, king),
                    message = null,
                ).normalized()
            }
        }
    }

    fun setFen(text: String) {
        _state.update { it.copy(fen = text, message = null) }
        val rules = _state.value.rules
        val board = runCatching { Board.fromFen(text, rules) }.getOrNull()
        if (board == null) {
            _state.update { it.copy(problems = listOf("FEN ist ungültig oder unvollständig.")) }
        } else {
            _state.update { it.fromBoard(board) }
        }
    }

    fun reset() = _state.update { it.fromBoard(Board.initial(it.rules)) }

    fun clear() = _state.update {
        it.fromBoard(Board.empty(it.rules)).copy(castling = Castling.none())
    }

    fun randomChess960() {
        val fen = Chess960Rules.randomStartFen(Random.Default)
        val board = Board.fromFen(fen, Chess960Rules)
        _state.update { it.copy(variantId = Chess960Rules.id).fromBoard(board) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (!current.canPlay) return
        val name = current.name.trim().ifEmpty {
            "Stellung ${(current.savedId ?: 0) + 1}"
        }
        val now = System.currentTimeMillis()
        val record = SavedPosition(
            id = current.savedId ?: 0,
            name = name,
            fen = current.fen,
            variant = current.variantId,
            createdAt = now,
            updatedAt = now,
        )
        viewModelScope.launch {
            val id = repository.save(record)
            _state.update { it.copy(savedId = id, name = name, message = "Gespeichert.") }
            onSaved(id)
        }
    }

    private fun outermostRook(board: Board, color: Color, king: Int, kingSide: Boolean): Int {
        val rank = rankOf(king)
        val files = if (kingSide) (fileOf(king) + 1..7) else (0 until fileOf(king)).reversed()
        for (file in files) {
            val square = rank * 8 + file
            val piece = board.pieceAt(square) ?: continue
            if (piece.type == PieceType.ROOK && piece.color == color) return square
        }
        return -1
    }

    private fun EditorState.toBoard(): Board =
        Board.fromPieces(
            squares = pieces.toTypedArray(),
            sideToMove = sideToMove,
            castling = castling,
            rules = rules,
        )

    /** Board → Zustand (Stellung übernehmen). */
    private fun EditorState.fromBoard(board: Board): EditorState = copy(
        pieces = board.snapshot(),
        sideToMove = board.sideToMove,
        castling = board.castling,
    ).normalized()

    /** Rechte aufräumen, FEN und Prüfmeldungen neu berechnen. */
    private fun EditorState.normalized(): EditorState {
        val board = toBoard()
        val pruned = pruneCastling(board, castling)
        val finalBoard = if (pruned === castling) board else Board.fromPieces(
            squares = board.snapshot().toTypedArray(),
            sideToMove = board.sideToMove,
            castling = pruned,
            rules = board.rules,
        )
        return copy(
            castling = pruned,
            fen = finalBoard.fen(),
            problems = board.rules.validate(finalBoard),
        )
    }

    private fun pruneCastling(board: Board, castling: Castling): Castling {
        var result = Castling.none()
        for (color in Color.entries) {
            for (kingSide in booleanArrayOf(true, false)) {
                if (!castling.has(color, kingSide)) continue
                val king = castling.kingFrom(color)
                val rook = castling.rookFrom(color, kingSide)
                if (board.kingSquare(color) != king) continue
                val rookPiece = if (rook in 0..63) board.pieceAt(rook) else null
                if (rookPiece == null || rookPiece.type != PieceType.ROOK || rookPiece.color != color) continue
                result = result.withRight(color, kingSide, rook, king)
            }
        }
        return result
    }
}
