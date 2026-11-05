package ca.cheungeric.lounge.ui.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientLayoutTest {

    @Test fun staysInsideFreeArea() {
        for (m in 0L until 2_000L) {
            val o = AmbientLayout.offsetFor(29_000_000L + m, 1200, 700)
            assertTrue("x=${o.x}", o.x in 0..1200)
            assertTrue("y=${o.y}", o.y in 0..700)
        }
    }

    @Test fun deterministic() {
        assertEquals(AmbientLayout.offsetFor(12345L, 800, 400), AmbientLayout.offsetFor(12345L, 800, 400))
    }

    @Test fun movesEveryMinute() {
        for (m in 0L until 2_000L) {
            assertNotEquals(AmbientLayout.offsetFor(m, 50, 30), AmbientLayout.offsetFor(m + 1, 50, 30))
        }
    }

    @Test fun movesEvenInOneAxis() {
        for (m in 0L until 500L) {
            assertNotEquals(AmbientLayout.offsetFor(m, 3, 0).x, AmbientLayout.offsetFor(m + 1, 3, 0).x)
            assertNotEquals(AmbientLayout.offsetFor(m, 0, 1).y, AmbientLayout.offsetFor(m + 1, 0, 1).y)
            assertTrue(AmbientLayout.offsetFor(m, 0, 1).y in 0..1)
        }
    }

    @Test fun spreadsOverTheArea() {
        val xs = (0L until 500L).map { AmbientLayout.offsetFor(it, 1000, 1000).x }
        assertTrue(xs.minOrNull()!! < 200)
        assertTrue(xs.maxOrNull()!! > 800)
    }

    @Test fun noRoomOrNegativeGivesZero() {
        assertEquals(AmbientOffset(0, 0), AmbientLayout.offsetFor(7L, 0, 0))
        assertEquals(AmbientOffset(0, 0), AmbientLayout.offsetFor(7L, -40, -10))
    }
}

class AmbientIdleTest {

    @Test fun offNeverShows() {
        assertEquals(AmbientIdle.Decision.Off, AmbientIdle.decide(0, 0L, 99_999_999L, onPlainHome = true))
    }

    @Test fun blockedOffHome() {
        assertEquals(AmbientIdle.Decision.Blocked, AmbientIdle.decide(10, 0L, 99_999_999L, onPlainHome = false))
    }

    @Test fun waitsRemainingTime() {
        assertEquals(AmbientIdle.Decision.Wait(60_000L), AmbientIdle.decide(5, 1_000L, 1_000L + 4 * 60_000L, true))
    }

    @Test fun showsAtAndAfterTimeout() {
        assertEquals(AmbientIdle.Decision.Show, AmbientIdle.decide(10, 0L, 600_000L, true))
        assertEquals(AmbientIdle.Decision.Show, AmbientIdle.decide(10, 0L, 700_000L, true))
        assertEquals(AmbientIdle.Decision.Wait(1L), AmbientIdle.decide(10, 0L, 599_999L, true))
    }

    @Test fun normalizeFallsBackToDefault() {
        assertEquals(10, AmbientIdle.normalize(7))
        assertEquals(10, AmbientIdle.normalize(-1))
        AmbientIdle.OPTIONS.forEach { assertEquals(it, AmbientIdle.normalize(it)) }
        assertEquals(listOf(0, 5, 10, 30), AmbientIdle.OPTIONS)
        assertEquals(10, AmbientIdle.DEFAULT_MINUTES)
    }
}
