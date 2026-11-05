package ca.cheungeric.lounge.home

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exists only so MediaSessionManager.getActiveSessions(ComponentName) is allowed once the user grants
 * Notification access (SPEC "v3 → TV hooks"). Notifications themselves are never read.
 */
class MediaAccessService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        _connected.value = false
        super.onListenerDisconnected()
    }

    companion object {
        private val _connected = MutableStateFlow(false)

        /** True while the system has the listener bound (a hint for MediaWatcher to retry). */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }
}
