package org.example.chesshost.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.chesshost.App
import org.example.chesshost.chess.Pgn
import org.example.chesshost.data.SavedGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as App).container.gameRepository

    val games: StateFlow<List<SavedGame>> = repository.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<SavedGame?>(null)
    val selected: StateFlow<SavedGame?> = _selected

    fun load(id: Long) {
        viewModelScope.launch { _selected.value = repository.find(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun deleteAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    /**
     * Importiert eine PGN-Partie aus der Zwischenablage. Liefert die ID der
     * gespeicherten Partie oder null, wenn sich keine Züge lesen ließen.
     */
    fun importPgn(text: String, onResult: (Long?) -> Unit) {
        val replay = Pgn.replay(text)
        if (replay.moveCount == 0) {
            onResult(null)
            return
        }
        viewModelScope.launch {
            val id = repository.save(
                SavedGame(
                    whiteName = replay.whiteName,
                    blackName = replay.blackName,
                    mode = MODE_IMPORT,
                    result = replay.result,
                    termination = "Import",
                    pgn = text.trim(),
                    moveCount = replay.moveCount,
                    playedAt = System.currentTimeMillis(),
                    variant = replay.rules.id,
                ),
            )
            onResult(id)
        }
    }

    companion object {
        const val MODE_IMPORT = "import"
    }
}
