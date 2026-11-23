package org.example.chesshost.engine

/**
 * Gemeinsame Schnittstelle für alle Engines (Stockfish-Prozess oder interne
 * Fallback-Engine). Jede Instanz ist unabhängig – für "Engine gegen Engine"
 * laufen zwei Instanzen parallel.
 */
interface ChessEngine : AutoCloseable {

    val displayName: String

    /** Startet die Engine. Gibt false zurück, wenn sie nicht verfügbar ist. */
    suspend fun start(): Boolean

    /**
     * Sucht den besten Zug.
     *
     * @param startFen Stellung am Partiebeginn (Stockfish bekommt darüber die volle Zugliste).
     * @param uciHistory alle bisherigen Züge in UCI-Notation
     * @param currentFen aktuelle Stellung (Fallback-Engine startet direkt hiervon)
     */
    suspend fun bestMove(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): String?

    /**
     * Bewertet die Stellung samt bester Variante. Engines ohne Bewertung
     * (etwa die interne Fallback-Engine) liefern null.
     */
    suspend fun evaluate(
        startFen: String,
        uciHistory: List<String>,
        currentFen: String,
        config: EngineConfig,
    ): EngineEval? = null

    /** Bricht eine laufende Suche ab und wartet, bis die Engine bereit ist. */
    suspend fun stopSearch()

    override fun close()
}
