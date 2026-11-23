package org.example.schach.engine.tournament

import org.example.schach.chess.Board
import org.example.schach.data.TournamentGame
import org.example.schach.data.TournamentParticipant
import org.example.schach.ui.tournament.Standings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TournamentLogicTest {

    private fun participant(id: String) = TournamentParticipant(
        id = id,
        label = id,
        difficulty = "CLUB",
        skillLevel = 6,
        moveTimeMs = 500,
        threads = 1,
        hashMb = 32,
        elo = null,
    )

    private fun game(
        number: Int,
        white: String,
        black: String,
        result: String,
    ) = TournamentGame(
        tournamentId = 1,
        gameNumber = number,
        round = 1,
        whiteId = white,
        blackId = black,
        whiteLabel = white,
        blackLabel = black,
        result = result,
        termination = "Matt",
        openingName = null,
        startFen = null,
        moveCount = 40,
        pgn = "",
        playedAt = 0,
    )

    @Test
    fun `zwei engines spielen 100 partien mit ausgeglichenen farben`() {
        val schedule = PairingSchedule.schedule(listOf("a", "b"), 100)

        assertEquals(100, schedule.size)
        val whiteA = schedule.count { it.whiteId == "a" }
        assertEquals(50, whiteA)
        assertEquals(50, schedule.count { it.whiteId == "b" })
        // Aufeinanderfolgende Partien wechseln die Farben.
        assertEquals("a", schedule[0].whiteId)
        assertEquals("b", schedule[1].whiteId)
    }

    @Test
    fun `drei engines spielen jede paarung mit beiden farben`() {
        val schedule = PairingSchedule.schedule(listOf("a", "b", "c"), 6)

        assertEquals(6, schedule.size)
        assertEquals(1, schedule.count { it.whiteId == "a" && it.blackId == "b" })
        assertEquals(1, schedule.count { it.whiteId == "b" && it.blackId == "a" })
        assertEquals(1, schedule.count { it.whiteId == "c" && it.blackId == "b" })
        val rounds = schedule.map { it.round }.toSet()
        assertEquals(1, rounds.size)
    }

    @Test
    fun `tabelle zaehlt siege remis und niederlagen richtig`() {
        val participants = listOf(participant("a"), participant("b"))
        val games = listOf(
            game(1, "a", "b", "1-0"),
            game(2, "b", "a", "1/2-1/2"),
            game(3, "a", "b", "0-1"),
            game(4, "b", "a", "1-0"),
        )

        val table = Standings.compute(participants, games)

        val a = table.rows.first { it.participant.id == "a" }
        val b = table.rows.first { it.participant.id == "b" }
        assertEquals(1, a.wins)
        assertEquals(1, a.draws)
        assertEquals(2, a.losses)
        assertEquals(1.5, a.points, 0.0001)
        assertEquals(2, b.wins)
        assertEquals(2.5, b.points, 0.0001)
        // Sieger steht oben, auch wenn alphabetisch "a" vorne läge.
        assertEquals("b", table.rows.first().participant.id)
    }

    @Test
    fun `kreuztabelle trennt siege remis und niederlagen`() {
        val participants = listOf(participant("a"), participant("b"), participant("c"))
        val games = listOf(
            game(1, "a", "b", "1-0"),
            game(2, "a", "b", "0-1"),
            game(3, "a", "c", "1/2-1/2"),
        )

        val cell = Standings.compute(participants, games).crosstable["a" to "b"]

        assertNotNull(cell)
        assertEquals(1, cell!!.wins)
        assertEquals(1, cell.losses)
        assertEquals(2, cell.games)
        assertNull(Standings.compute(participants, games).crosstable["b" to "c"])
    }

    @Test
    fun `punktausbeute wird in elo umgerechnet`() {
        assertNull(Standings.scoreToElo(1.0))
        assertNull(Standings.scoreToElo(0.0))
        assertEquals(0, Standings.scoreToElo(0.5))
        assertTrue(Standings.scoreToElo(0.75)!! > 100)
        assertTrue(Standings.scoreToElo(0.25)!! < -100)
    }

    @Test
    fun `konfidenzintervall wird bei mehr partien schmaler`() {
        val few = Standings.wilsonInterval(0.75, 4)
        val many = Standings.wilsonInterval(0.75, 100)

        assertTrue(many.endInclusive - many.start < few.endInclusive - few.start)
        assertTrue(few.start in 0.0..1.0)
        assertTrue(few.endInclusive in 0.0..1.0)
    }

    @Test
    fun `ohne partien bleibt die elo schaetzung leer`() {
        val row = Standings.compute(listOf(participant("a")), emptyList()).rows.single()

        assertEquals(0, row.games)
        assertNull(row.eloDiff)
        assertNull(row.eloDiffRange)
    }

    @Test
    fun `eroeffnungsbuch mischt und beginnt von vorn`() {
        val book = OpeningBook(
            listOf(
                Opening("A", "fen-a"),
                Opening("B", "fen-b"),
            ),
        )

        assertEquals("A", book.forGame(1)?.name)
        assertEquals("B", book.forGame(2)?.name)
        assertEquals("A", book.forGame(3)?.name)
        assertNull(OpeningBook(emptyList()).forGame(1))
    }

    @Test
    fun `alle ausgelieferten eroeffnungen sind gueltige stellungen`() {
        val candidates = listOf(
            File("src/main/assets/openings/standard.json"),
            File("app/src/main/assets/openings/standard.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("Eröffnungsdatei nicht gefunden", file)

        val root = JSONObject(file!!.readText())
        val array = root.getJSONArray("openings")
        assertTrue("Zu wenige Eröffnungen", array.length() >= 20)

        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            val fen = json.getString("fen")
            val board = Board.fromFen(fen)
            assertTrue("Eröffnung ${json.getString("name")} hat keine Züge", board.hasLegalMove())
        }
    }
}
