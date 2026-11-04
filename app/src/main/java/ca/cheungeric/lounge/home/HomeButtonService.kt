package ca.cheungeric.lounge.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import ca.cheungeric.lounge.BuildConfig
import ca.cheungeric.lounge.MainActivity

/**
 * Brings Lounge forward when Home is pressed (SPEC "Home button"). All decisions live in
 * [HomeDecider]; this class only adapts Android events and starts [MainActivity].
 */
class HomeButtonService : AccessibilityService() {

    private var decider: HomeDecider? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        decider = HomeDecider(packageName)
        // Re-assert the XML config at runtime; some OEM builds ignore parts of the XML.
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            info.notificationTimeout = 100
            serviceInfo = info
        } catch (t: Throwable) {
            debug("serviceInfo update failed: ${t.javaClass.simpleName}")
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val d = decider ?: return false
        return try {
            val decision = d.onKey(event.keyCode, event.action, event.repeatCount, now())
            if (decision.launch) launchLounge("key")
            decision.consume
        } catch (t: Throwable) {
            debug("onKeyEvent failed: ${t.javaClass.simpleName}")
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val d = decider ?: return
        try {
            val pkg = event.packageName?.toString()
            val foreground = loungeResumed || pkg == packageName
            if (d.onWindowChanged(pkg, now(), foreground, allowStockUntil)) launchLounge("window")
        } catch (t: Throwable) {
            debug("onAccessibilityEvent failed: ${t.javaClass.simpleName}")
        }
    }

    override fun onInterrupt() {
        // no-op: we give no spoken/haptic feedback that could be interrupted.
    }

    override fun onDestroy() {
        decider = null
        super.onDestroy()
    }

    private fun launchLounge(source: String) {
        // NEW_TASK: required to start an activity from a service context. With MainActivity
        // being singleTask, the existing instance's task is brought forward and the intent is
        // delivered to onNewIntent (no recreation). REORDER_TO_FRONT keeps that "bring forward,
        // don't rebuild" behaviour on builds that treat the task differently. CLEAR_TOP is
        // deliberately not used: it is redundant under singleTask and would disable
        // REORDER_TO_FRONT.
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_FROM_HOME_BUTTON, true)
        try {
            startActivity(intent)
            debug("launched Lounge via $source")
        } catch (t: Throwable) {
            debug("launch failed: ${t.javaClass.simpleName}")
        }
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "LoungeHome"

        /** Boolean extra on the launch intent; MainActivity.onNewIntent can reset to Home. */
        const val EXTRA_FROM_HOME_BUTTON = "ca.cheungeric.lounge.FROM_HOME_BUTTON"

        @Volatile
        private var allowStockUntil: Long = Long.MIN_VALUE

        @Volatile
        private var loungeResumed: Boolean = false

        /**
         * Call right before Lounge deliberately opens the stock launcher or system Settings.
         * Until [timeMs] (SystemClock.uptimeMillis() clock) a stock launcher window will not
         * bounce back to Lounge; once the stock launcher is entered during that window, it
         * stays allowed until the user leaves it. Example: `allowStockLauncherUntil(
         * SystemClock.uptimeMillis() + 3_000)`.
         */
        @JvmStatic
        fun allowStockLauncherUntil(timeMs: Long) {
            allowStockUntil = timeMs
        }

        /**
         * MainActivity should call this from onResume(true) / onPause(false) so the window
         * fallback never relaunches while Lounge is already in front. Safe to never call.
         */
        @JvmStatic
        fun setLoungeResumed(resumed: Boolean) {
            loungeResumed = resumed
        }
    }
}
