package ca.cheungeric.lounge.ui.patchnotes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewGateTest {
    private val notes = listOf(
        PatchNote(3, "2026-11-20", "Three", listOf("c")),
        PatchNote(2, "2026-11-10", "Two", listOf("b")),
        PatchNote(1, "2026-11-04", "One", listOf("a")),
    )

    @Test
    fun freshInstallShowsNothing() {
        assertTrue(WhatsNewGate.decide(freshInstall = true, lastSeen = null, current = 3, notes = notes).isEmpty())
    }

    @Test
    fun missingLastSeenNotFreshShowsLatestOnly() {
        assertEquals(listOf(3), WhatsNewGate.decide(false, null, 3, notes).map { it.v })
    }

    @Test
    fun olderLastSeenShowsDelta() {
        assertEquals(listOf(3, 2), WhatsNewGate.decide(false, 1, 3, notes).map { it.v })
        assertEquals(listOf(3), WhatsNewGate.decide(false, 2, 3, notes).map { it.v })
    }

    @Test
    fun sameVersionShowsNothing() {
        assertTrue(WhatsNewGate.decide(false, 3, 3, notes).isEmpty())
    }

    @Test
    fun lastSeenAheadOfCurrentShowsNothing() {
        assertTrue(WhatsNewGate.decide(false, 5, 3, notes).isEmpty())
    }

    @Test
    fun freshWinsEvenIfLastSeenPresent() {
        assertTrue(WhatsNewGate.decide(true, 1, 3, notes).isEmpty())
    }
}
