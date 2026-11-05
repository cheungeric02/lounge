package ca.cheungeric.lounge.home

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import ca.cheungeric.lounge.home.HomeServiceStatus.SettingsTarget

/** Whether Notification access (for Now Playing) is granted, and a shortcut to grant it. */
object MediaAccessStatus {

    fun component(context: Context): ComponentName = ComponentName(context, MediaAccessService::class.java)

    /** NotificationManager.isNotificationListenerAccessGranted is API 27+ (minSdk is 28). */
    fun isGranted(context: Context): Boolean = try {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.isNotificationListenerAccessGranted(component(context)) == true
    } catch (t: Throwable) {
        false
    }

    private const val TV_SETTINGS_PKG = "com.android.tv.settings"

    /**
     * Tried in order (SPEC): the standard action, then Android TV Settings' special-access screens,
     * then Settings itself. Each must resolve to an exported activity before it's started.
     */
    val NOTIFICATION_ACCESS_TARGETS: List<SettingsTarget> = listOf(
        SettingsTarget(action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        SettingsTarget(TV_SETTINGS_PKG, "$TV_SETTINGS_PKG.device.apps.specialaccess.NotificationAccessActivity"),
        SettingsTarget(TV_SETTINGS_PKG, "$TV_SETTINGS_PKG.device.apps.specialaccess.SpecialAppAccessActivity"),
        SettingsTarget(action = Settings.ACTION_SETTINGS),
    )

    /** Opens Notification access settings; false if nothing could be started (manual steps are always shown). */
    fun openSettings(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = HomeServiceStatus.startableInOrder(NOTIFICATION_ACCESS_TARGETS) { t ->
            pm.resolveActivity(intentFor(t), 0)?.activityInfo?.exported == true
        }
        for (target in candidates) {
            try {
                HomeButtonService.allowStockLauncherUntil(android.os.SystemClock.uptimeMillis() + 3_000)
                context.startActivity(intentFor(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                // try next
            } catch (e: SecurityException) {
                // try next
            }
        }
        return false
    }

    private fun intentFor(t: SettingsTarget): Intent =
        if (t.pkg != null && t.cls != null) {
            Intent().setComponent(ComponentName(t.pkg, t.cls))
        } else {
            Intent(t.action)
        }
}
