package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.remote.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeNowPlayingTest {
    private fun s(state: Int, title: String = "Song") =
        NowPlaying.Session(pkg = "com.spotify.tv.android", app = "Spotify", state = state, title = title)

    @Test fun nullWhenNoSession() = assertNull(HomeNowPlaying.of(null, null))

    @Test fun nullWhenStoppedOrBuffering() {
        assertNull(HomeNowPlaying.of(s(1), null))
        assertNull(HomeNowPlaying.of(s(6), null))
    }

    @Test fun playingAndPaused() {
        val p = HomeNowPlaying.of(s(NowPlaying.STATE_PLAYING), null)!!
        assertTrue(p.playing); assertEquals("Song", p.title); assertEquals("Spotify", p.app)
        assertFalse(HomeNowPlaying.of(s(NowPlaying.STATE_PAUSED), null)!!.playing)
    }

    @Test fun blankTitleFallsBackToApp() =
        assertEquals("Spotify", HomeNowPlaying.of(s(NowPlaying.STATE_PLAYING, " "), null)!!.title)
}
