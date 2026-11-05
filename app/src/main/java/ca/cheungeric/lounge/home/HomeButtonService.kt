package ca.cheungeric.lounge.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import ca.cheungeric.lounge.BuildConfig
import ca.cheungeric.lounge.LoungeApp
import ca.cheungeric.lounge.MainActivity
import ca.cheungeric.lounge.remote.InputSnapshot
import ca.cheungeric.lounge.remote.ProfilePicker
import ca.cheungeric.lounge.remote.RemoteCommand
import ca.cheungeric.lounge.remote.TvInput
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Brings Lounge forward when Home is pressed (SPEC "Home button"). All decisions live in
 * [HomeDecider]; this class only adapts Android events and starts [MainActivity].
 */
class HomeButtonService : AccessibilityService() {

    private var decider: HomeDecider? = null

    /** True while this instance holds a RemoteBridge reference (released exactly once). */
    private val bridgeHeld = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        decider = HomeDecider(packageName)
        connected = this
        if (bridgeHeld.compareAndSet(false, true)) {
            try {
                (application as LoungeApp).container.remoteBridge.acquire()
            } catch (t: Throwable) {
                bridgeHeld.set(false)
                debug("remote bridge acquire failed: ${t.javaClass.simpleName}")
            }
        }
        // Re-assert the XML config at runtime; some OEM builds ignore parts of the XML.
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
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
        if (event == null) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val d = decider
            if (d != null) {
                try {
                    val pkg = event.packageName?.toString()
                    val foreground = loungeResumed || pkg == packageName
                    if (d.onWindowChanged(pkg, now(), foreground, allowStockUntil)) launchLounge("window")
                } catch (t: Throwable) {
                    debug("onAccessibilityEvent failed: ${t.javaClass.simpleName}")
                }
            }
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) {
            trackInput()
        }
    }

    /**
     * Phone typing (SPEC "v3 → Typing"): publish only the focused, editable, non-password field.
     * Skipped entirely while Phone remote is off, so nothing is read when nobody can use it.
     */
    private fun trackInput() {
        try {
            if (!remoteEnabled()) {
                TvInput.set(InputSnapshot.NONE)
                return
            }
            val node = focusedInput()
            val snap = if (node == null) {
                InputSnapshot.NONE
            } else {
                try {
                    TvInput.fromNode(
                        present = true,
                        editable = node.isEditable,
                        focused = node.isFocused,
                        password = node.isPassword,
                        pkg = node.packageName,
                        hint = node.hintText,
                        // Read text only after the editable/password checks inside fromNode pass.
                        text = if (node.isEditable && !node.isPassword) node.text else null,
                        showingHint = node.isShowingHintText,
                    )
                } finally {
                    recycle(node)
                }
            }
            TvInput.set(snap)
        } catch (t: Throwable) {
            debug("trackInput failed: ${t.javaClass.simpleName}")
        }
    }

    private fun remoteEnabled(): Boolean = try {
        (application as LoungeApp).container.remoteBridge.isEnabled
    } catch (t: Throwable) {
        false
    }

    /** The node with input focus across interactive windows, or null. Caller recycles. */
    private fun focusedInput(): AccessibilityNodeInfo? = try {
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    } catch (t: Throwable) {
        null
    }

    /** ACTION_SET_TEXT on the focused editable node. */
    private fun setFocusedText(text: String): Boolean {
        val node = focusedInput() ?: return false
        return try {
            if (!node.isEditable) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            recycle(node)
        }
    }

    /** IME Enter on the focused editable node (API 30+; ignored below). */
    private fun submitFocused(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val node = focusedInput() ?: return false
        return try {
            if (!node.isEditable) return false
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } finally {
            recycle(node)
        }
    }

    // ---- v4: auto-pick a profile on YouTube's "Who's watching?" screen ----

    private val pickHandler = Handler(Looper.getMainLooper())
    private var pickPkg: String? = null
    private var pickProfile: String? = null
    private var pickUntil = 0L
    private val pickTick = object : Runnable {
        override fun run() {
            val pkg = pickPkg
            val profile = pickProfile
            if (pkg == null || profile == null || now() > pickUntil) {
                stopPick()
                return
            }
            if (tryPick(pkg, profile)) {
                debug("auto-picked profile")
                stopPick()
            } else {
                pickHandler.postDelayed(this, ProfilePicker.POLL_MS)
            }
        }
    }

    private fun startPick(pkg: String, profile: String) {
        pickHandler.removeCallbacks(pickTick)
        pickPkg = pkg
        pickProfile = profile
        pickUntil = now() + ProfilePicker.WINDOW_MS
        pickHandler.postDelayed(pickTick, ProfilePicker.POLL_MS)
    }

    private fun stopPick() {
        pickHandler.removeCallbacks(pickTick)
        pickPkg = null
        pickProfile = null
    }

    /** Snapshot the target app's window, find the profile, click it. Never throws. */
    private fun tryPick(pkg: String, profile: String): Boolean {
        val root = try {
            windows?.firstOrNull { it.root?.packageName?.toString() == pkg }?.root
                ?: rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
        } catch (t: Throwable) {
            null
        } ?: return false
        val infos = ArrayList<AccessibilityNodeInfo>()
        val nodes = ArrayList<ProfilePicker.Node>()
        try {
            infos += root
            nodes += ProfilePicker.Node(root.text?.toString(), root.contentDescription?.toString(), root.isClickable, -1)
            var i = 0
            while (i < infos.size && infos.size < ProfilePicker.MAX_NODES) {
                val n = infos[i]
                for (c in 0 until n.childCount) {
                    val child = runCatching { n.getChild(c) }.getOrNull() ?: continue
                    infos += child
                    nodes += ProfilePicker.Node(
                        child.text?.toString(), child.contentDescription?.toString(), child.isClickable, i,
                    )
                    if (infos.size >= ProfilePicker.MAX_NODES) break
                }
                i++
            }
            val target = ProfilePicker.target(nodes, profile) ?: return false
            val node = infos[target]
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                (node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        } catch (t: Throwable) {
            debug("tryPick failed: ${t.javaClass.simpleName}")
            return false
        } finally {
            infos.forEach(::recycle)
        }
    }

    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            runCatching { node.recycle() }
        }
    }

    override fun onInterrupt() {
        // no-op: we give no spoken/haptic feedback that could be interrupted.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        decider = null
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        stopPick()
        if (connected === this) {
            connected = null
            TvInput.set(InputSnapshot.NONE)
        }
        if (bridgeHeld.compareAndSet(true, false)) {
            try {
                (application as LoungeApp).container.remoteBridge.release()
            } catch (t: Throwable) {
                debug("remote bridge release failed: ${t.javaClass.simpleName}")
            }
        }
    }

    private fun launchLounge(source: String) {
        val intent = loungeIntent(this)
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

        /** The connected instance (cleared in onUnbind/onDestroy) for [performBack]. */
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var connected: HomeButtonService? = null

        /**
         * The "bring Lounge forward" intent used for the Home key (and the phone remote's Home).
         * NEW_TASK: required to start an activity from a service context. With MainActivity
         * being singleTask, the existing instance's task is brought forward and the intent is
         * delivered to onNewIntent (no recreation). REORDER_TO_FRONT keeps that "bring forward,
         * don't rebuild" behaviour on builds that treat the task differently. CLEAR_TOP is
         * deliberately not used: it is redundant under singleTask and would disable
         * REORDER_TO_FRONT.
         */
        @JvmStatic
        fun loungeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(EXTRA_FROM_HOME_BUTTON, true)

        /** Global Back via the running service (phone remote). False if the service isn't connected. */
        @JvmStatic
        fun performBack(): Boolean = try {
            connected?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        } catch (t: Throwable) {
            false
        }

        /**
         * Phone launch with a profile (v4): for [ProfilePicker.WINDOW_MS], look for [profile] on
         * [pkg]'s account picker and click it. No-op if the service isn't connected. Main thread.
         */
        @JvmStatic
        fun armProfilePick(pkg: String, profile: String) {
            try {
                connected?.startPick(pkg, profile)
            } catch (t: Throwable) {
                // best-effort
            }
        }

        /**
         * v5 phone D-pad: Up/Down/Left/Right/Ok → GLOBAL_ACTION_DPAD_* (API 33+). Back/Home are
         * handled by the bridge's existing paths, so they return false here. False below API 33
         * or when the service isn't connected. Call on the main thread.
         */
        @JvmStatic
        fun dpad(key: RemoteCommand.KeyName): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val action = when (key) {
                RemoteCommand.KeyName.Up -> GLOBAL_ACTION_DPAD_UP
                RemoteCommand.KeyName.Down -> GLOBAL_ACTION_DPAD_DOWN
                RemoteCommand.KeyName.Left -> GLOBAL_ACTION_DPAD_LEFT
                RemoteCommand.KeyName.Right -> GLOBAL_ACTION_DPAD_RIGHT
                RemoteCommand.KeyName.Ok -> GLOBAL_ACTION_DPAD_CENTER
                RemoteCommand.KeyName.Back, RemoteCommand.KeyName.Home -> return false
            }
            return try {
                connected?.performGlobalAction(action) ?: false
            } catch (t: Throwable) {
                false
            }
        }

        /** v5 Power fallback: puts the box to sleep (HDMI-CEC may then turn the TV off). Main thread. */
        @JvmStatic
        fun sleepBox(): Boolean = try {
            connected?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        } catch (t: Throwable) {
            false
        }

        /** tv/status `keys`: D-pad commands work (service connected AND API 33+). */
        @JvmStatic
        fun isKeysSupported(): Boolean = connected != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** True while the service is connected (tv/status `typing`). */
        @JvmStatic
        fun isRunning(): Boolean = connected != null

        /** Phone `text`: replaces the focused editable field's value. Call on the main thread. */
        @JvmStatic
        fun setText(text: String): Boolean = try {
            connected?.setFocusedText(text) ?: false
        } catch (t: Throwable) {
            false
        }

        /** Phone `submit`: IME Enter on the focused field (API 30+). Call on the main thread. */
        @JvmStatic
        fun submit(): Boolean = try {
            connected?.submitFocused() ?: false
        } catch (t: Throwable) {
            false
        }

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
