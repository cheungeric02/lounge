package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.remote.NowPlaying.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {

    private fun s(
        pkg: String,
        state: Int,
        pos: Long = 0,
        at: Long = 0,
        dur: Long = 600_000,
        speed: Float = 1f,
        actions: Long = 0,
        title: String = "T",
    ) = Session(pkg, pkg, state, title, "", dur, pos, at, speed, actions)

    @Test
    fun choose_firstPlaying() {
        val a = s("a.a", NowPlaying.STATE_PAUSED, at = 900)
        val b = s("b.b", NowPlaying.STATE_PLAYING, at = 100)
        val c = s("c.c", NowPlaying.STATE_PLAYING, at = 500)
        assertEquals(b, NowPlaying.choose(listOf(a, b, c)))
    }

    @Test
    fun choose_mostRecentPausedWhenNothingPlays() {
        val a = s("a.a", NowPlaying.STATE_PAUSED, at = 100)
        val b = s("b.b", NowPlaying.STATE_PAUSED, at = 900)
        val stopped = s("c.c", 1, at = 5000)
        assertEquals(b, NowPlaying.choose(listOf(a, stopped, b)))
    }

    @Test
    fun choose_noneWhenNoPlayingOrPaused() {
        assertNull(NowPlaying.choose(emptyList()))
        assertNull(NowPlaying.choose(listOf(s("a.a", 0), s("b.b", 1), s("c.c", 6))))
    }

    @Test
    fun positionAt_extrapolatesWhilePlaying() {
        val p = s("a.a", NowPlaying.STATE_PLAYING, pos = 10_000, at = 1_000)
        assertEquals(15_000, NowPlaying.positionAt(p, 6_000))
        val fast = p.copy(speed = 2f)
        assertEquals(20_000, NowPlaying.positionAt(fast, 6_000))
        // Clock before the sample: no negative progress.
        assertEquals(10_000, NowPlaying.positionAt(p, 500))
    }

    @Test
    fun positionAt_pausedIsFrozen_andClamped() {
        val p = s("a.a", NowPlaying.STATE_PAUSED, pos = 10_000, at = 1_000)
        assertEquals(10_000, NowPlaying.positionAt(p, 99_000))
        val nearEnd = s("a.a", NowPlaying.STATE_PLAYING, pos = 595_000, at = 0, dur = 600_000).copy(lastPositionUpdateMs = 1)
        assertEquals(600_000, NowPlaying.positionAt(nearEnd, 60_000))
        val unknown = s("a.a", NowPlaying.STATE_PAUSED, pos = -1)
        assertEquals(0, NowPlaying.positionAt(unknown, 0))
        val live = s("a.a", NowPlaying.STATE_PLAYING, pos = 5_000, at = 1, dur = 0)
        assertEquals(10_001, NowPlaying.positionAt(live, 5_002))
    }

    @Test
    fun seekTarget_clamps() {
        assertEquals(40_000, NowPlaying.seekTarget(30_000, 10_000, 600_000))
        assertEquals(20_000, NowPlaying.seekTarget(30_000, -10_000, 600_000))
        assertEquals(0, NowPlaying.seekTarget(5_000, -10_000, 600_000))
        assertEquals(600_000, NowPlaying.seekTarget(595_000, 10_000, 600_000))
        assertEquals(Long.MAX_VALUE, NowPlaying.seekTarget(Long.MAX_VALUE - 1, 10, 0))
    }

    @Test
    fun capabilities() {
        assertTrue(s("a.a", 3, actions = NowPlaying.ACTION_SEEK_TO).canSeek)
        assertFalse(s("a.a", 3, actions = NowPlaying.ACTION_SEEK_TO, dur = 0).canSeek)
        assertFalse(s("a.a", 3, actions = 0).canSeek)
        assertTrue(s("a.a", 3, actions = NowPlaying.ACTION_SKIP_TO_NEXT).canSkip)
        assertTrue(s("a.a", 3, actions = NowPlaying.ACTION_SKIP_TO_PREVIOUS).canSkip)
        assertFalse(s("a.a", 3, actions = NowPlaying.ACTION_SEEK_TO).canSkip)
    }

    @Test
    fun payload_playing() {
        val p = Session(
            "com.spotify.tv.android", "Spotify", NowPlaying.STATE_PLAYING, "Song", "Artist",
            200_000, 10_000, 1_000, 1f, NowPlaying.ACTION_SEEK_TO or NowPlaying.ACTION_SKIP_TO_NEXT,
        )
        val o = Json.parseToJsonElement(NowPlaying.payload(p, "abc", 3_000, 1_800_000_000_000)).jsonObject
        assertEquals("playing", o["state"]!!.jsonPrimitive.content)
        assertEquals("com.spotify.tv.android", o["pkg"]!!.jsonPrimitive.content)
        assertEquals("Spotify", o["app"]!!.jsonPrimitive.content)
        assertEquals("Song", o["title"]!!.jsonPrimitive.content)
        assertEquals("Artist", o["subtitle"]!!.jsonPrimitive.content)
        assertEquals("200000", o["durationMs"]!!.jsonPrimitive.content)
        assertEquals("12000", o["positionMs"]!!.jsonPrimitive.content)
        assertEquals("1800000000000", o["positionAt"]!!.jsonPrimitive.content)
        assertEquals(1.0, o["speed"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("abc", o["artHash"]!!.jsonPrimitive.content)
        assertEquals("true", o["canSeek"]!!.jsonPrimitive.content)
        assertEquals("true", o["canSkip"]!!.jsonPrimitive.content)
        assertEquals(12, o.size)
    }

    @Test
    fun payload_pausedAndNone() {
        val p = s("a.a", NowPlaying.STATE_PAUSED, pos = 7_000, at = 1)
        val o = Json.parseToJsonElement(NowPlaying.payload(p, "", 99_999, 5)).jsonObject
        assertEquals("paused", o["state"]!!.jsonPrimitive.content)
        assertEquals("7000", o["positionMs"]!!.jsonPrimitive.content)
        val n = Json.parseToJsonElement(NowPlaying.payload(null, "ignored", 0, 5)).jsonObject
        assertEquals("none", n["state"]!!.jsonPrimitive.content)
        assertEquals("", n["artHash"]!!.jsonPrimitive.content)
        assertEquals("false", n["canSeek"]!!.jsonPrimitive.content)
        assertEquals(12, n.size)
    }

    @Test
    fun needsRepublish_ignoresExpectedProgress() {
        val a = s("a.a", NowPlaying.STATE_PLAYING, pos = 10_000, at = 1_000)
        // App re-posts state 1 s later at the expected position.
        assertFalse(NowPlaying.needsRepublish(a, a.copy(positionMs = 11_000, lastPositionUpdateMs = 2_000)))
        // A seek.
        assertTrue(NowPlaying.needsRepublish(a, a.copy(positionMs = 60_000, lastPositionUpdateMs = 2_000)))
        // State / metadata changes.
        assertTrue(NowPlaying.needsRepublish(a, a.copy(state = NowPlaying.STATE_PAUSED)))
        assertTrue(NowPlaying.needsRepublish(a, a.copy(title = "Next")))
        assertTrue(NowPlaying.needsRepublish(null, a))
        assertTrue(NowPlaying.needsRepublish(a, null))
        assertFalse(NowPlaying.needsRepublish(null, null))
        // Paused seek is a change.
        val p = s("a.a", NowPlaying.STATE_PAUSED, pos = 10_000, at = 1_000)
        assertTrue(NowPlaying.needsRepublish(p, p.copy(positionMs = 20_000, lastPositionUpdateMs = 5_000)))
        assertFalse(NowPlaying.needsRepublish(p, p.copy(lastPositionUpdateMs = 5_000)))
    }

    @Test
    fun fitWithin_320() {
        assertEquals(320 to 180, NowPlaying.fitWithin(1920, 1080, 320))
        assertEquals(180 to 320, NowPlaying.fitWithin(1080, 1920, 320))
        assertEquals(320 to 320, NowPlaying.fitWithin(1000, 1000, 320))
        assertEquals(100 to 50, NowPlaying.fitWithin(100, 50, 320))
        assertEquals(320 to 1, NowPlaying.fitWithin(10_000, 1, 320))
        assertEquals(1 to 1, NowPlaying.fitWithin(0, 5, 320))
    }
}
