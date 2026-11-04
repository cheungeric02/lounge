package ca.cheungeric.lounge.home

import ca.cheungeric.lounge.home.HomeDecider.Companion.ACTION_DOWN
import ca.cheungeric.lounge.home.HomeDecider.Companion.ACTION_UP
import ca.cheungeric.lounge.home.HomeDecider.Companion.KEYCODE_HOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDeciderTest {
    private val own = "ca.cheungeric.lounge"
    private val gtv = "com.google.android.apps.tv.launcherx"
    private val app = "com.netflix.ninja"
    private val d = HomeDecider(own)

    private fun tap(at: Long, holdMs: Long = 100): Pair<KeyDecision, KeyDecision> =
        d.onKey(KEYCODE_HOME, ACTION_DOWN, 0, at) to d.onKey(KEYCODE_HOME, ACTION_UP, 0, at + holdMs)

    private fun win(pkg: String?, at: Long, fg: Boolean = false, allow: Long = Long.MIN_VALUE) =
        d.onWindowChanged(pkg, at, fg, allow)

    @Test fun homeTapConsumesBothAndLaunchesOnce() {
        val (down, up) = tap(1_000)
        assertEquals(KeyDecision.CONSUME, down)
        assertEquals(KeyDecision.CONSUME_AND_LAUNCH, up)
        // The stock launcher showing up afterwards must not cause a second launch.
        assertFalse(win(gtv, 1_300))
    }

    @Test fun otherKeysPassThrough() {
        assertEquals(KeyDecision.PASS, d.onKey(23, ACTION_DOWN, 0, 1_000))
        assertEquals(KeyDecision.PASS, d.onKey(23, ACTION_UP, 0, 1_050))
        assertFalse(d.homeKeySeen)
    }

    @Test fun longPressByRepeatIsNotConsumedOrLaunched() {
        assertEquals(KeyDecision.CONSUME, d.onKey(KEYCODE_HOME, ACTION_DOWN, 0, 1_000))
        assertEquals(KeyDecision.PASS, d.onKey(KEYCODE_HOME, ACTION_DOWN, 1, 1_400))
        assertEquals(KeyDecision.PASS, d.onKey(KEYCODE_HOME, ACTION_UP, 0, 1_450))
        assertFalse(win(gtv, 1_500))
    }

    @Test fun longPressByHoldDurationIsNotLaunched() {
        val (_, up) = tap(1_000, holdMs = 501)
        assertEquals(KeyDecision.PASS, up)
        assertFalse(win(gtv, 1_600))
    }

    @Test fun upWithoutDownPassesThrough() {
        assertEquals(KeyDecision.PASS, d.onKey(KEYCODE_HOME, ACTION_UP, 0, 1_000))
    }

    @Test fun windowFallbackWithinWindowAfterUnlaunchedPress() {
        // First tap launches; second tap inside the 700 ms debounce is consumed, not launched.
        tap(1_000)
        val (_, up2) = tap(1_200)
        assertEquals(KeyDecision.CONSUME, up2)
        // Stock launcher appears 1.4 s after the second press: fallback fires once.
        assertTrue(win(gtv, 2_600))
        assertFalse(win(app, 2_700))
        assertFalse(win(gtv, 2_800))
    }

    @Test fun windowFallbackAfter1500msIsIgnored() {
        tap(1_000)
        tap(1_200) // debounced, no launch
        assertFalse(win(gtv, 1_200 + 1_501))
    }

    @Test fun nonLauncherWindowNeverLaunches() {
        assertFalse(win(app, 1_000))
        assertFalse(win("com.android.tv.settings", 2_000))
    }

    @Test fun noKeyModeTransitionToStockLauncherLaunches() {
        assertTrue(win(gtv, 1_000))
        assertFalse(win(own, 1_100))
        assertFalse(win(app, 5_000))
        assertTrue(win("com.google.android.tvlauncher", 6_000))
    }

    @Test fun noKeyModeRepeatedLauncherEventsCountOnce() {
        assertTrue(win(gtv, 1_000))
        assertFalse(win(gtv, 2_000)) // still in launcher, no new transition
        assertFalse(win("com.android.systemui", 2_500)) // overlay ignored
        assertFalse(win(gtv, 3_000))
    }

    @Test fun keyModeDisablesNoKeyFallback() {
        tap(1_000)
        assertFalse(win(app, 5_000))
        assertFalse(win(gtv, 9_000)) // no recent press
    }

    @Test fun allowStockLauncherSuppressesUntilLeft() {
        assertFalse(win(gtv, 1_000, allow = 2_000))
        // Allowed stint persists after the timestamp while still in the launcher.
        assertFalse(win("com.android.systemui", 3_000))
        assertFalse(win(gtv, 4_000))
        // Leaving and coming back re-arms the fallback.
        assertFalse(win(app, 5_000))
        assertTrue(win(gtv, 6_000))
    }

    @Test fun alreadyForegroundSuppresses() {
        assertFalse(win(gtv, 1_000, fg = true))
    }

    @Test fun debounceMinGapBetweenLaunches() {
        assertTrue(win(gtv, 1_000))
        assertFalse(win(app, 1_100))
        assertFalse(win(gtv, 1_500)) // < 700 ms since last launch
        assertFalse(win(app, 1_600))
        assertTrue(win(gtv, 1_800))
    }

    @Test fun keyDebounceConsumesButDoesNotLaunch() {
        assertEquals(KeyDecision.CONSUME_AND_LAUNCH, tap(1_000).second)
        assertEquals(KeyDecision.CONSUME, tap(1_300).second)
        assertEquals(KeyDecision.CONSUME_AND_LAUNCH, tap(2_000).second)
    }

    @Test fun noKeyModeLoopBreakerBacksOff() {
        var t = 1_000L
        repeat(HomeDecider.LOOP_MAX_LAUNCHES) {
            assertTrue(win(gtv, t)); win(own, t + 100); t += 1_000
        }
        assertFalse(win(gtv, t)) // 4th within 10 s trips the breaker
        win(own, t + 100)
        assertFalse(win(gtv, t + 5_000))
        win(app, t + 5_100)
        assertTrue(win(gtv, t + HomeDecider.LOOP_BACKOFF_MS + 1))
    }

    @Test fun statusParserMatchesLongAndShortForms() {
        val cls = "ca.cheungeric.lounge.home.HomeButtonService"
        assertTrue(HomeServiceStatus.isServiceInList("$own/$cls", own, cls))
        assertTrue(HomeServiceStatus.isServiceInList("a.b/a.b.X:$own/.home.HomeButtonService", own, cls))
        assertFalse(HomeServiceStatus.isServiceInList("a.b/a.b.X", own, cls))
        assertFalse(HomeServiceStatus.isServiceInList(null, own, cls))
        assertFalse(HomeServiceStatus.isServiceInList("", own, cls))
    }
}
