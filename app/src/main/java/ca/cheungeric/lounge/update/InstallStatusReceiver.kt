package ca.cheungeric.lounge.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/** Receives PackageInstaller session results and forwards them to [UpdateStatus]. Never throws. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = intent.confirmIntent()
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirm)
                        UpdateStatus.state.value = UpdateState.Installing
                    } else {
                        UpdateStatus.state.value = UpdateState.Failed("Android didn't show the install prompt. Try again.", UpdateStatus.lastInfo)
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    // Usually the process is replaced before this lands; if not, we are on the new build.
                    UpdateStatus.state.value = UpdateState.UpToDate
                }
                else -> {
                    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    UpdateStatus.state.value = UpdateState.Failed(failureMessage(status, msg), UpdateStatus.lastInfo)
                }
            }
        } catch (e: Exception) {
            UpdateStatus.state.value = UpdateState.Failed("Install failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun Intent.confirmIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun failureMessage(status: Int, detail: String?): String {
        val base = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install was cancelled."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install was blocked by the device."
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Install conflicts with the installed app (different signing key? uninstall first)."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update isn't compatible with this device."
            PackageInstaller.STATUS_FAILURE_INVALID -> "The downloaded APK is invalid."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install the update."
            else -> "Install failed."
        }
        return if (detail.isNullOrBlank()) base else "$base ($detail)"
    }
}
