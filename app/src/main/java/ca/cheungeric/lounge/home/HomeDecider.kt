package ca.cheungeric.lounge.home

/** What the accessibility key filter should do with one key event. */
data class KeyDecision(
    /** true = swallow the event so the system never sees it. */
    val consume: Boolean,
    /** true = bring Lounge to the front now. */
    val launch: Boolean,
) {
    companion object {
        val PASS = KeyDecision(consume = false, launch = false)
        val CONSUME = KeyDecision(consume = true, launch = false)
        val CONSUME_AND_LAUNCH = KeyDecision(consume = true, launch = true)
    }
}

/**
 * Pure (Android-free) decision logic for the Home-button override, so it can be unit-tested
 * on the JVM. [HomeButtonService] feeds it key events and window-state changes and acts on
 * the answers. Not thread-safe; the service calls it from the main thread only.
 *
 * Key path: a short Home tap (down + up within [LONG_PRESS_MS]) is consumed (both halves)
 * and launches Lounge. A long hold (key repeat seen, or up arrives after [LONG_PRESS_MS])
 * is not treated as a press: repeats and the up pass through and nothing is launched.
 *
 * Window fallback: when a stock launcher window appears, launch Lounge if a Home press was
 * seen within [PRESS_WINDOW_MS] and it has not already caused a launch. Many Android TV
 * builds never deliver KEYCODE_HOME to accessibility key filters, so if no Home key event
 * has ever been seen since the service started, any transition *into* a stock launcher
 * window counts as a Home press.
 *
 * Never launches while Lounge itself is foreground, while the stock launcher is explicitly
 * allowed ([allowStockUntil], set when Lounge opens the stock launcher or Settings on
 * purpose), more than once per press, more often than every [MIN_LAUNCH_GAP_MS], or (in
 * no-key mode) more than [LOOP_MAX_LAUNCHES] times in [LOOP_WINDOW_MS] — a loop breaker in
 * case a device keeps re-asserting its stock launcher.
 */
class HomeDecider(private val ownPackage: String) {

    /** True once any KEYCODE_HOME event has reached the key filter since service start. */
    var homeKeySeen: Boolean = false
        private set

    /** Time of the last short Home press (key-down time), or null if none yet. */
    var lastHomePressAt: Long? = null
        private set

    private var pendingDownAt: Long? = null
    private var pendingDownConsumed = false
    private var pendingIsLong = false

    private var lastLaunchAt: Long? = null
    private var launchedForPressAt: Long? = null

    /** Last window package seen (ignoring null / [IGNORED_PACKAGES]). */
    private var lastWindowPkg: String? = null

    /** The current stint in the stock launcher was explicitly allowed; lasts until we leave it. */
    private var stockSessionAllowed = false

    private val fallbackLaunchTimes = ArrayDeque<Long>()
    private var loopBackoffUntil: Long = Long.MIN_VALUE

    fun onKey(keyCode: Int, action: Int, repeatCount: Int, now: Long): KeyDecision {
        if (keyCode != KEYCODE_HOME) return KeyDecision.PASS
        homeKeySeen = true
        return when (action) {
            ACTION_DOWN -> onHomeDown(repeatCount, now)
            ACTION_UP -> onHomeUp(now)
            else -> KeyDecision.PASS
        }
    }

    private fun onHomeDown(repeatCount: Int, now: Long): KeyDecision {
        if (repeatCount > 0) {
            // Held: this is a long press. Let the system have it (dashboard / assistant).
            pendingIsLong = true
            return KeyDecision.PASS
        }
        pendingDownAt = now
        pendingDownConsumed = true
        pendingIsLong = false
        return KeyDecision.CONSUME
    }

    private fun onHomeUp(now: Long): KeyDecision {
        val downAt = pendingDownAt
        val downConsumed = pendingDownConsumed
        val wasLong = pendingIsLong || (downAt != null && now - downAt > LONG_PRESS_MS)
        pendingDownAt = null
        pendingDownConsumed = false
        pendingIsLong = false

        // No down seen (e.g. service started mid-press) or a long press: hands off.
        if (downAt == null || !downConsumed || wasLong) return KeyDecision.PASS

        lastHomePressAt = downAt
        if (!launchGapOk(now)) return KeyDecision.CONSUME
        recordLaunch(now, pressAt = downAt)
        return KeyDecision.CONSUME_AND_LAUNCH
    }

    /**
     * A window-state change to [pkg]. [loungeForeground] = Lounge's activity is currently
     * resumed; [allowStockUntil] = stock launcher is explicitly allowed until this time.
     * Returns true if Lounge should be launched now.
     */
    fun onWindowChanged(
        pkg: String?,
        now: Long,
        loungeForeground: Boolean,
        allowStockUntil: Long = Long.MIN_VALUE,
    ): Boolean {
        if (pkg == null || pkg in IGNORED_PACKAGES) return false
        val previous = lastWindowPkg
        lastWindowPkg = pkg

        if (pkg !in STOCK_LAUNCHERS) {
            stockSessionAllowed = false
            return false
        }
        if (now <= allowStockUntil) {
            stockSessionAllowed = true
            return false
        }
        if (stockSessionAllowed) return false
        if (loungeForeground || pkg == ownPackage) return false
        if (!launchGapOk(now)) return false

        if (homeKeySeen) {
            val pressAt = lastHomePressAt ?: return false
            if (now - pressAt > PRESS_WINDOW_MS || now < pressAt) return false
            if (launchedForPressAt == pressAt) return false
            recordLaunch(now, pressAt)
            return true
        }

        // No-key mode: entering the stock launcher is the Home press.
        if (previous != null && previous in STOCK_LAUNCHERS) return false
        if (now < loopBackoffUntil) return false
        while (fallbackLaunchTimes.isNotEmpty() && now - fallbackLaunchTimes.first() > LOOP_WINDOW_MS) {
            fallbackLaunchTimes.removeFirst()
        }
        if (fallbackLaunchTimes.size >= LOOP_MAX_LAUNCHES) {
            loopBackoffUntil = now + LOOP_BACKOFF_MS
            fallbackLaunchTimes.clear()
            return false
        }
        fallbackLaunchTimes.addLast(now)
        recordLaunch(now, pressAt = now)
        return true
    }

    private fun launchGapOk(now: Long): Boolean {
        val last = lastLaunchAt ?: return true
        return now - last >= MIN_LAUNCH_GAP_MS || now < last
    }

    private fun recordLaunch(now: Long, pressAt: Long) {
        lastLaunchAt = now
        launchedForPressAt = pressAt
    }

    companion object {
        // Mirrors android.view.KeyEvent so this class stays JVM-pure.
        const val KEYCODE_HOME = 3
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1

        const val LONG_PRESS_MS = 500L
        const val PRESS_WINDOW_MS = 1500L
        const val MIN_LAUNCH_GAP_MS = 700L
        const val LOOP_WINDOW_MS = 10_000L
        const val LOOP_MAX_LAUNCHES = 3
        const val LOOP_BACKOFF_MS = 30_000L

        /** Stock TV launchers (SPEC "Home button"). */
        val STOCK_LAUNCHERS: Set<String> = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )

        /** Overlays (volume, toasts) that shouldn't count as leaving/entering a launcher. */
        val IGNORED_PACKAGES: Set<String> = setOf("com.android.systemui")
    }
}
