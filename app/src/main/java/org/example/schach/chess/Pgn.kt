package org.example.schach.chess

/** Eine Stellung im Verlauf einer PGN-Partie. */
data class PgnStep(
    val fen: String,
    /** Zug, der zu dieser Stellung führte (null am Anfang). */
    val san: String?,
    val lastMove: Move?,
)

/** Ausgewertete PGN-Partie: Kopfzeilen, Regelwerk und alle Stellungen. */
class PgnReplay(
    val tags: Map<String, String>,
    val rules: RuleSet,
    val steps: List<PgnStep>,
) {
    val moveCount: Int get() = steps.size - 1
    val whiteName: String get() = tags["White"] ?: "?"
    val blackName: String get() = tags["Black"] ?: "?"
    val result: String get() = tags["Result"] ?: "*"
    val date: String get() = tags["Date"] ?: ""

    fun sanMoves(): List<String> = steps.drop(1).mapNotNull { it.san }
}

/**
 * Kleiner PGN-Leser: Kopfzeilen, Zugtokens und Nachspielen. Kommentare,
 * Varianten und Zugnummern werden ignoriert; unbekannte Züge beenden die
 * Auswertung, ohne die App zu stören.
 */
object Pgn {

    // Android prüft Regexe mit ICU und lehnt unescapte Klammern ab – `\]` und
    // `\}` müssen deshalb auch dort escaped sein, wo Java sie als Literal akzeptiert.
    private val tagRegex = Regex("""\[\s*(\w+)\s+"((?:[^"\\]|\\.)*)"\s*\]""")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*", "½-½")
    private val IGNORED = setOf("e.p.", "ep", "...", "..")

    /** Kopfzeilen einer PGN-Partie. */
    fun tags(text: String): Map<String, String> =
        tagRegex.findAll(text).associate {
            it.groupValues[1] to it.groupValues[2].replace("\\\"", "\"")
        }

    /** Zugtokens (SAN) aus dem Zugteil. */
    fun moves(text: String): List<String> {
        val body = tagRegex.replace(text, " ")
        val cleaned = body
            .replace(Regex("\\{[^}]*\\}"), " ")
            .replace(Regex(";[^\\n]*"), " ")
            .replace(Regex("\\([^()]*\\)"), " ")
            .replace(Regex("\\$\\d+"), " ")
            .replace(Regex("\\d+\\.(\\.\\.)?"), " ")
        return cleaned.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it in RESULTS }
            .filterNot { it in IGNORED }
            .filterNot { it.startsWith('$') }
            // Buchstaben für Figuren, „0“ für die Rochade in Null-Schreibweise.
            .filter { it.first().isLetter() || it.first() == '0' }
    }

    /** Spielt eine PGN-Partie nach. */
    fun replay(text: String, fallback: RuleSet = Rules.standard): PgnReplay {
        val tags = tags(text)
        val variantTag = tags["Variant"].orEmpty()
        val rules = if (variantTag.contains("960")) Chess960Rules else fallback
        val startFen = tags["FEN"] ?: Board.initial(rules).fen()
        val game = runCatching { ChessGame(startFen, rules) }.getOrElse { ChessGame() }
        val steps = mutableListOf(PgnStep(game.board.fen(), null, null))
        for (san in moves(text)) {
            val move = game.moveFromSan(san) ?: break
            game.makeMove(move)
            steps += PgnStep(game.board.fen(), game.sanMoves.lastOrNull() ?: san, move)
        }
        return PgnReplay(tags, rules, steps)
    }

    /** Sucht den legalen Zug zu einer SAN-Schreibweise. */
    fun ChessGame.moveFromSan(raw: String): Move? {
        val wanted = normalizeSan(raw)
        if (wanted.isEmpty()) return null
        val legal = legalMoves()
        return legal.firstOrNull { normalizeSan(rules.san(board, it, legal)) == wanted }
    }

    private fun normalizeSan(san: String): String =
        san.replace("0-0-0", "O-O-O")
            .replace("0-0", "O-O")
            .filterNot { it in "+#!?" }
            .trim()
}
