package org.example.schach.engine.tournament

import org.example.schach.chess.Board
import org.example.schach.chess.PieceType
import org.example.schach.data.Tournament
import org.example.schach.data.TournamentGame
import org.example.schach.data.TournamentParticipant
import org.example.schach.engine.SimpleEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Spielt echte Partien mit der internen Engine durch, um die Turnierschleife zu
 * prüfen: Reihenfolge, Ergebniserfassung, PGN und Sonderfälle. Die Engine-Auswahl
 * ist über `openEngine` austauschbar, deshalb braucht der Test kein Android.
 */
class TournamentRunnerTest {

    private fun participant(id: String) = TournamentParticipant(
        id = id,
        label = id,
        difficulty = "BEGINNER",
        skillLevel = 0,
        moveTimeMs = 1,
        threads = 1,
        hashMb = 0,
        elo = null,
    )

    private fun tournament(
        games: Int,
        useOpenings: Boolean,
        variant: String = "standard",
        timeControlId: String = "correspondence",
    ) = Tournament(
        id = 1,
        name = "Testturnier",
        createdAt = 0,
        status = Tournament.STATUS_RUNNING,
        plannedGames = games,
        playedGames = 0,
        useOpenings = useOpenings,
        moveTimeMs = 1,
        participantsJson = org.example.schach.data.TournamentParticipant.listToJson(
            listOf(participant("a"), participant("b")),
        ),
        variant = variant,
        timeControlId = timeControlId,
    )

    @Test
    fun `spielt die geplanten partien und meldet fortschritt`() = runBlocking {
        val played = mutableListOf<TournamentGame>()
        val progress = mutableListOf<Int>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(42)) })

        runner.run(
            tournament = tournament(games = 2, useOpenings = false),
            openingBook = OpeningBook(emptyList()),
            onProgress = { progress.add(it.gameNumber) },
            onGame = { played.add(it) },
        )

        assertEquals(2, played.size)
        assertEquals(listOf(1, 2), progress)
        assertEquals(listOf(1, 2), played.map { it.gameNumber })
        // Farben wechseln im Spielplan.
        assertEquals("a" to "b", played[0].whiteId to played[0].blackId)
        assertEquals("b" to "a", played[1].whiteId to played[1].blackId)
        played.forEach { game ->
            assertTrue("keine Züge in Partie ${game.gameNumber}", game.moveCount > 0)
            assertTrue(game.result in setOf("1-0", "0-1", "1/2-1/2"))
            assertTrue(game.pgn.contains("[Event \"Turnier: Testturnier\"]"))
            assertTrue("PGN ohne Züge", game.pgn.length > 100)
        }
    }

    @Test
    fun `mit eroeffnungen startet jede partie von einer anderen stellung`() = runBlocking {
        val book = OpeningBook(
            listOf(
                Opening("Test A", "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"),
                Opening("Test B", "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2"),
            ),
        )
        val played = mutableListOf<TournamentGame>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(7)) })

        runner.run(
            tournament = tournament(games = 2, useOpenings = true),
            openingBook = book,
            onProgress = { },
            onGame = { played.add(it) },
        )

        assertEquals(listOf("Test A", "Test B"), played.map { it.openingName })
        played.forEach { game ->
            assertNotNull(game.startFen)
            assertTrue(game.startFen != Board.START_FEN)
            assertTrue(game.pgn.contains("[SetUp \"1\"]"))
            assertTrue(game.pgn.contains("[Opening "))
        }
    }

    @Test
    fun `ohne eroeffnungen starten alle partien von der grundstellung`() = runBlocking {
        val played = mutableListOf<TournamentGame>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(1)) })

        runner.run(
            tournament = tournament(games = 1, useOpenings = false),
            openingBook = OpeningBook(listOf(Opening("ignoriert", "8/8/8/8/8/8/8/K6k w - - 0 1"))),
            onProgress = { },
            onGame = { played.add(it) },
        )

        assertNull(played.single().openingName)
        assertNull(played.single().startFen)
    }

    @Test
    fun `schach960-turnier spielt von verschiedenen grundstellungen`() = runBlocking {
        val played = mutableListOf<TournamentGame>()
        val book = OpeningBook.chess960(seed = 99, count = 4)
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(3)) })

        runner.run(
            tournament = tournament(games = 2, useOpenings = true, variant = "chess960"),
            openingBook = book,
            onProgress = { },
            onGame = { played.add(it) },
        )

        assertEquals(2, played.size)
        played.forEach { game ->
            assertTrue(game.pgn.contains("[Variant \"Chess960\"]"))
            assertTrue(game.result in setOf("1-0", "0-1", "1/2-1/2"))
        }
        // Die erste Partie startet von der (960-notierten) Grundstellung, die zweite von einer Zufallsstellung.
        assertNull(played[0].startFen)
        val custom = played[1]
        assertNotNull("960-Partie ohne Startstellung", custom.startFen)
        assertTrue(custom.pgn.contains("[SetUp \"1\"]"))
        assertTrue(custom.startFen != played[0].startFen)
    }

    @Test
    fun `live-brett zeigt die stellung der laufenden partie`() = runBlocking {
        val live = mutableListOf<TournamentLiveGame>()
        val played = mutableListOf<TournamentGame>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(11)) })

        runner.run(
            tournament = tournament(games = 1, useOpenings = false),
            openingBook = OpeningBook(emptyList()),
            onProgress = { },
            onGame = { played.add(it) },
            onLive = { live.add(it) },
        )

        val game = played.single()
        // Ein Eintrag zum Partiebeginn, danach einer je Zug.
        assertEquals(game.moveCount + 1, live.size)

        val first = live.first()
        assertEquals(Board.START_FEN, first.fen)
        assertTrue(first.sanMoves.isEmpty())
        assertNull(first.lastMove)

        val last = live.last()
        assertEquals(game.moveCount, last.sanMoves.size)
        assertNotNull(last.lastMove)
        assertEquals("a" to "b", last.whiteLabel to last.blackLabel)

        // Jede Zwischenstellung ist ein vollständiges Brett mit zwei Königen.
        live.forEach { snapshot ->
            assertEquals(64, snapshot.pieces.size)
            assertEquals(2, snapshot.pieces.count { it?.type == PieceType.KING })
            assertTrue("FEN fehlt", snapshot.fen.split(" ").size >= 4)
        }
        // Die Zugliste wächst monoton – das Brett folgt der Partie Zug für Zug.
        assertEquals(live.map { it.sanMoves.size }.sorted(), live.map { it.sanMoves.size })
    }

    @Test
    fun `mit uhr steht die bedenkzeit im pgn und auf dem live-brett`() = runBlocking {
        val played = mutableListOf<TournamentGame>()
        val live = mutableListOf<TournamentLiveGame>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(5)) })

        runner.run(
            tournament = tournament(
                games = 1,
                useOpenings = false,
                timeControlId = "blitz_3_2",
            ),
            openingBook = OpeningBook(emptyList()),
            onProgress = { },
            onGame = { played.add(it) },
            onLive = { live.add(it) },
        )

        val game = played.single()
        assertTrue(game.pgn.contains("[TimeControl \"180+2\"]"))
        // Jeder gemeldete Stand trägt die Uhren beider Seiten.
        assertTrue(live.isNotEmpty())
        assertTrue(live.all { it.clocked })
        assertTrue(live.all { it.whiteTimeMs >= 0 && it.blackTimeMs >= 0 })
    }

    @Test
    fun `ohne uhr gibt es keine bedenkzeit im pgn`() = runBlocking {
        val played = mutableListOf<TournamentGame>()
        val runner = TournamentRunner(openEngine = { _, _ -> SimpleEngine(Random(9)) })

        runner.run(
            tournament = tournament(games = 1, useOpenings = false),
            openingBook = OpeningBook(emptyList()),
            onProgress = { },
            onGame = { played.add(it) },
        )

        assertTrue(!played.single().pgn.contains("[TimeControl "))
    }

    @Test
    fun `engine die nicht startet bricht das turnier ab`() {
        val runner = TournamentRunner(openEngine = { _, _ -> error("Engine fehlt") })

        var failed = false
        try {
            runBlocking {
                runner.run(
                    tournament = tournament(games = 1, useOpenings = false),
                    openingBook = OpeningBook(emptyList()),
                    onProgress = { },
                    onGame = { },
                )
            }
        } catch (expected: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
    }
}
