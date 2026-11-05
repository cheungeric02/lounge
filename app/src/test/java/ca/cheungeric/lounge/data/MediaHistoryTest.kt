package ca.cheungeric.lounge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaHistoryTest {

    private fun e(pkg: String, title: String, at: Long = 0, art: String? = null) =
        HistoryEntry(pkg, "App $pkg", title, null, art, at)

    private fun up(list: List<HistoryEntry>, pkg: String, title: String, now: Long, art: String? = null, sub: String? = null) =
        MediaHistory.upsert(list, pkg, "App $pkg", title, sub, art, now)

    @Test fun upsert_addsToFront() {
        val u = up(listOf(e("a", "One", 1)), "b", "Two", 100)
        assertTrue(u.changed)
        assertEquals(listOf("Two", "One"), u.entries.map { it.title })
        assertEquals(100, u.entries[0].updatedAtMs)
        assertTrue(u.evicted.isEmpty())
    }

    @Test fun upsert_movesExistingToFront_andKeepsArt() {
        val list = listOf(e("a", "One", 1), e("b", "Two", 2, art = "two.png"))
        val u = up(list, "b", "Two", 50_000)
        assertEquals(listOf("Two", "One"), u.entries.map { it.title })
        assertEquals("two.png", u.entries[0].artFile)
        assertEquals(2, u.entries.size)
        assertTrue(u.evicted.isEmpty())
    }

    @Test fun upsert_sameTitleDifferentApp_isSeparate() {
        val u = up(listOf(e("a", "News")), "b", "News", 10)
        assertEquals(2, u.entries.size)
    }

    @Test fun upsert_throttlesRepeatedWritesForFrontEntry() {
        val list = listOf(e("a", "One", 1_000))
        assertFalse(up(list, "a", "One", 1_000 + 29_999).changed)
        assertTrue(up(list, "a", "One", 1_000 + 30_000).changed)
        // A new subtitle or new art is written at once.
        assertTrue(up(list, "a", "One", 2_000, sub = "S1E2").changed)
        assertTrue(up(list, "a", "One", 2_000, art = "x.png").changed)
        // Not first → always written (it moves to the front).
        assertTrue(up(listOf(e("b", "Two", 1_000)) + list, "a", "One", 2_000).changed)
    }

    @Test fun upsert_blankSubtitleStoredAsNull() {
        assertNull(up(emptyList(), "a", "One", 1, sub = "  ").entries[0].subtitle)
    }

    @Test fun upsert_trimsToMax_andReportsEvictedArt() {
        val list = (0 until MediaHistory.MAX_ENTRIES).map { e("p$it", "T$it", it.toLong(), art = "a$it.png") }
        val u = up(list, "new", "New", 99)
        assertEquals(MediaHistory.MAX_ENTRIES, u.entries.size)
        assertEquals("New", u.entries.first().title)
        assertEquals(listOf("T11"), u.evicted.map { it.title })
        assertFalse(u.entries.any { it.title == "T11" })
    }

    @Test fun evictedArt_skipsSharedOrMissingArt() {
        val before = listOf(e("a", "1", art = "x.png"), e("b", "2", art = "x.png"), e("c", "3"))
        val after = listOf(before[0])
        assertTrue(MediaHistory.evictedArt(before, after).isEmpty())
        assertEquals(listOf("1", "2"), MediaHistory.evictedArt(before, listOf(e("z", "z"))).map { it.title })
    }

    @Test fun dedupe_firstWins() {
        val d = MediaHistory.dedupe(listOf(e("a", "1", 5), e("b", "1"), e("a", "1", 1)))
        assertEquals(listOf(5L, 0L), d.map { it.updatedAtMs })
    }

    @Test fun remove() {
        val list = listOf(e("a", "1"), e("b", "2"))
        val (rest, gone) = MediaHistory.remove(list, "a", "1")
        assertEquals(listOf("2"), rest.map { it.title })
        assertEquals("1", gone!!.title)
        assertNull(MediaHistory.remove(list, "a", "nope").second)
    }

    @Test fun orphanArt() {
        assertEquals(listOf("old.png"), MediaHistory.orphanArt(listOf(e("a", "1", art = "x.png")), listOf("x.png", "old.png")))
    }

    @Test fun artFileName_stable() {
        val n = MediaHistory.artFileName("com.app", "Title")
        assertEquals(n, MediaHistory.artFileName("com.app", "Title"))
        assertNotEquals(n, MediaHistory.artFileName("com.app", "Title2"))
        assertTrue(n.matches(Regex("[0-9a-f]{16}\\.png")))
    }

    @Test fun codec_roundTrip_andCorrupt() {
        val list = listOf(e("a", "1", 7, art = "x.png"), HistoryEntry("b", "B", "2", "S1", null, 9))
        assertEquals(list, MediaHistory.decode(MediaHistory.encode(list)))
        assertTrue(MediaHistory.decode("{not json").isEmpty())
        assertTrue(MediaHistory.decode(null).isEmpty())
    }

    @Test fun relativeAge() {
        assertEquals("Just now", MediaHistory.relativeAge(30_000, 0))
        assertEquals("5 min ago", MediaHistory.relativeAge(5 * 60_000L, 0))
        assertEquals("2 h ago", MediaHistory.relativeAge(2 * 3_600_000L + 5, 0))
        assertEquals("3 d ago", MediaHistory.relativeAge(3 * 86_400_000L, 0))
        assertEquals("Just now", MediaHistory.relativeAge(0, 1_000))
    }
}
