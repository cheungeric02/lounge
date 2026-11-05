package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.remote.SleepTimer.Rearm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {
    private val now = 1_800_000_000_000L

    @Test
    fun atFor() {
        assertNull(SleepTimer.atFor(0, now))
        assertNull(SleepTimer.atFor(-5, now))
        assertEquals(now + 30 * 60_000L, SleepTimer.atFor(30, now))
        assertEquals(now + 240 * 60_000L, SleepTimer.atFor(240, now))
        assertEquals(now + 240 * 60_000L, SleepTimer.atFor(999, now))
    }

    @Test
    fun rearm_nothingStored() {
        assertEquals(Rearm.None, SleepTimer.rearm(null, now))
        assertEquals(Rearm.None, SleepTimer.rearm("null", now))
    }

    @Test
    fun rearm_future_arms() {
        val at = now + 10 * 60_000L
        assertEquals(Rearm.Arm(at, 10 * 60_000L), SleepTimer.rearm("""{"at":$at}""", now))
        assertEquals(Rearm.Arm(at, 10 * 60_000L), SleepTimer.rearm("""{"at":$at.0}""", now))
        // Slight clock skew past 240 min still accepted.
        val edge = now + 240 * 60_000L + 60_000L
        assertEquals(Rearm.Arm(edge, edge - now), SleepTimer.rearm("""{"at":$edge}""", now))
    }

    @Test
    fun rearm_expired_clears_neverFiresLate() {
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":$now}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":${now - 1}}""", now))
    }

    @Test
    fun rearm_garbage_clears() {
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":${now + 240 * 60_000L + 60_001L}}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":"${now + 1000}"}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":true}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{"at":null}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{}""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""[1]""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""5""", now))
        assertEquals(Rearm.Clear, SleepTimer.rearm("""{not json""", now))
    }
}
