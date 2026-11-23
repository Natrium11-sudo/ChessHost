package org.example.chesshost.ui.navigation

import android.util.Base64

object Routes {
    const val MENU = "menu"
    const val PLAY = "play?start={start}"
    const val PLAY_ROUTE = "play"
    const val ENGINE_MATCH = "engine_match?start={start}"
    const val ENGINE_MATCH_ROUTE = "engine_match"
    const val HISTORY = "history"
    const val STORE = "store"
    const val SETTINGS = "settings"
    const val LLM = "llm"
    const val GAME_DETAIL = "game/{id}"
    const val TOURNAMENTS = "tournaments"
    const val TOURNAMENT_SETUP = "tournament_setup"
    const val TOURNAMENT_DETAIL = "tournament/{id}"
    const val POSITIONS = "positions"
    const val POSITION_EDITOR = "position_editor/{id}"

    fun gameDetail(id: Long): String = "game/$id"

    fun tournamentDetail(id: Long): String = "tournament/$id"

    fun positionEditor(id: Long): String = "position_editor/$id"

    /**
     * Partie von einer Stellung starten. FEN und Regelwerk werden Base64-kodiert
     * (URL-sicher), weil eine FEN Leerzeichen und Schrägstriche enthält und
     * damit nicht direkt in eine Route passt.
     */
    fun play(fen: String, variantId: String): String {
        val spec = encodeStart(fen, variantId)
        return "$PLAY_ROUTE?start=$spec"
    }

    /** Engine-Duell von einer Stellung starten. */
    fun engineMatch(fen: String, variantId: String): String {
        val spec = encodeStart(fen, variantId)
        return "$ENGINE_MATCH_ROUTE?start=$spec"
    }

    fun encodeStart(fen: String, variantId: String): String =
        Base64.encodeToString(
            "$variantId|$fen".toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

    /** Liefert `variant|fen` oder null, wenn der Parameter kaputt ist. */
    fun decodeStart(spec: String?): Pair<String, String>? {
        if (spec.isNullOrBlank()) return null
        return runCatching {
            val text = String(
                Base64.decode(spec, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                Charsets.UTF_8,
            )
            val split = text.indexOf('|')
            if (split <= 0) return null
            text.substring(0, split) to text.substring(split + 1)
        }.getOrNull()
    }
}
