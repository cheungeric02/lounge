package ca.cheungeric.lounge.home

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Brings Lounge forward when Home is pressed. TODO(T5): implement per SPEC "Home button". */
class HomeButtonService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO(T5)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // TODO(T5)
        return false
    }

    override fun onInterrupt() {
        // no-op
    }
}
