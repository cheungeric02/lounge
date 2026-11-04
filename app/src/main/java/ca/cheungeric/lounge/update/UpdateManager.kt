package ca.cheungeric.lounge.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide update state, shared by every [UpdateManager] and by [InstallStatusReceiver]
 * (which has no reference to the manager).
 */
internal object UpdateStatus {
    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** Last update being installed, so a failure reported by the receiver can be retried. */
    @Volatile var lastInfo: VersionInfo? = null
}

/**
 * Owns the self-update flow. Create once (e.g. in AppContainer) with an app-lifetime scope, call
 * [start] when the UI comes up, observe [state].
 */
class UpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val checker: UpdateChecker = UpdateChecker(),
    private val installer: UpdateInstaller = UpdateInstaller(context.applicationContext),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val app = context.applicationContext
    private var autoJob: Job? = null

    val state: StateFlow<UpdateState> = UpdateStatus.state.asStateFlow()

    /**
     * Checks once now (SPEC: "on start"), then again whenever a day has passed since the last check,
     * for as long as [scope] lives. Idempotent. Auto-checks are silent: errors leave the state alone.
     */
    fun start() {
        if (autoJob?.isActive == true) return
        autoJob = scope.launch {
            runCheck(manual = false)
            while (isActive) {
                delay(POLL_MS)
                if (now() - lastCheck() >= DAY_MS) runCheck(manual = false)
            }
        }
    }

    fun stop() {
        autoJob?.cancel()
        autoJob = null
    }

    /** Settings → "Check for updates". Ends in Available, UpToDate or Failed. */
    fun checkNow() {
        scope.launch { runCheck(manual = true) }
    }

    /** Installs the update in [UpdateState.Available] (or the one carried by a failed/permission state). */
    fun install() {
        val info = when (val s = UpdateStatus.state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.PermissionRequired -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        } ?: return
        UpdateStatus.lastInfo = info
        scope.launch { installer.install(info) { UpdateStatus.state.value = it } }
    }

    /** Clears a terminal state (UpToDate/Failed) back to Idle, e.g. when the user dismisses a message. */
    fun dismiss() {
        val s = UpdateStatus.state.value
        if (s is UpdateState.UpToDate || s is UpdateState.Failed) UpdateStatus.state.value = UpdateState.Idle
    }

    private suspend fun runCheck(manual: Boolean) {
        if (isBusy(UpdateStatus.state.value)) return
        if (manual) UpdateStatus.state.value = UpdateState.Checking
        val result = checker.fetch()
        val cur = UpdateStatus.state.value
        if (cur is UpdateState.Downloading || cur is UpdateState.Installing) return
        result.onSuccess { info ->
            setLastCheck(now())
            UpdateStatus.state.value = when {
                checker.isUpdate(info) -> UpdateState.Available(info)
                manual -> UpdateState.UpToDate
                cur is UpdateState.Available -> UpdateState.Idle
                else -> cur
            }
        }.onFailure {
            if (manual) UpdateStatus.state.value = UpdateState.Failed("Couldn't check for updates. Check your connection.")
        }
    }

    private fun isBusy(s: UpdateState) =
        s is UpdateState.Checking || s is UpdateState.Downloading || s is UpdateState.Installing

    private fun lastCheck(): Long = try {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK, 0L)
    } catch (_: Exception) {
        0L
    }

    private fun setLastCheck(t: Long) {
        try {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK, t).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PREFS = "lounge_update"
        const val KEY_LAST_CHECK = "lounge_lastUpdateCheck"
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val POLL_MS = 60L * 60 * 1000
    }
}
