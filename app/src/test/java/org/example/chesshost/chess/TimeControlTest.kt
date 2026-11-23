package org.example.chesshost.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeControlTest {

    @Test
    fun `jede zeitklasse hat voreinstellungen`() {
        TimeCategory.entries.forEach { category ->
            assertTrue("Keine Bedenkzeit für ${category.label}", TimeControls.of(category).isNotEmpty())
        }
    }

    @Test
    fun `ids sind eindeutig und die auswahl findet jede wieder`() {
        val ids = TimeControls.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        TimeControls.all.forEach { control ->
            assertEquals(control, TimeControls.byId(control.id))
            assertEquals(control.category, TimeControls.byId(control.id).category)
        }
        // Unbekannte oder fehlende IDs fallen auf die Standard-Bedenkzeit zurück.
        assertEquals(TimeControls.default, TimeControls.byId(null))
        assertEquals(TimeControls.default, TimeControls.byId("gibt-es-nicht"))
    }

    @Test
    fun `die stufen passen zu ihrer zeitklasse`() {
        assertTrue(TimeControls.of(TimeCategory.BULLET).all { it.initialMs < 3 * 60_000L })
        assertTrue(
            TimeControls.of(TimeCategory.BLITZ).all { it.initialMs in 3 * 60_000L..5 * 60_000L },
        )
        assertTrue(TimeControls.of(TimeCategory.RAPID).all { it.initialMs >= 10 * 60_000L })
        assertTrue(TimeControls.of(TimeCategory.CLASSICAL).all { it.initialMs >= 30 * 60_000L })
    }

    @Test
    fun `fernschach laeuft ohne uhr`() {
        val correspondence = TimeControls.of(TimeCategory.CORRESPONDENCE).single()
        assertFalse(correspondence.clocked)
        assertEquals("Ohne Uhr", correspondence.label)
        assertEquals(0L, correspondence.initialMs)
        assertTrue(TimeControls.default.clocked)
    }

    @Test
    fun `notation nennt minuten und aufschlag`() {
        assertEquals("5 + 3", TimeControls.byId("blitz_5_3").notation)
        assertEquals("1 + 0", TimeControls.byId("bullet_1_0").notation)
        assertEquals("15 + 10", TimeControls.byId("rapid_15_10").notation)
    }

    @Test
    fun `die uhr wird als minuten und sekunden formatiert`() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-5_000))
        assertEquals("0:59", formatClock(59_400))
        assertEquals("5:00", formatClock(300_000))
        assertEquals("1:01", formatClock(61_000))
        assertEquals("1:00:00", formatClock(3_600_000))
    }

    @Test
    fun `unter zehn sekunden zeigt die uhr zehntel`() {
        assertEquals("0:10", formatClock(10_000))
        assertEquals("0:09.4", formatClock(9_400))
        assertEquals("0:00.9", formatClock(900))
        assertEquals("0:00", formatClock(0))
    }
}
