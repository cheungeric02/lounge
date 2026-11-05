package ca.cheungeric.lounge.home

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Whether the Home-button accessibility service is on, and a shortcut to turn it on. */
object HomeServiceStatus {

    fun isEnabled(context: Context): Boolean = try {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        isServiceInList(enabled, context.packageName, HomeButtonService::class.java.name)
    } catch (t: Throwable) {
        false
    }

    /** One way to open a settings screen: an explicit component, or an implicit action. */
    data class SettingsTarget(val pkg: String? = null, val cls: String? = null, val action: String? = null)

    private const val TV_SETTINGS_PKG = "com.android.tv.settings"

    /**
     * Tried in order. Explicit Android TV Settings components first: on some TVs the implicit
     * ACTION_ACCESSIBILITY_SETTINGS resolves, but TV Settings then shows "No application can handle
     * this action".
     */
    val ACCESSIBILITY_TARGETS: List<SettingsTarget> = listOf(
        SettingsTarget(TV_SETTINGS_PKG, "$TV_SETTINGS_PKG.system.AccessibilityActivity"),
        SettingsTarget(TV_SETTINGS_PKG, "$TV_SETTINGS_PKG.accessibility.AccessibilityActivity"),
        SettingsTarget(action = Settings.ACTION_ACCESSIBILITY_SETTINGS),
        SettingsTarget(action = Settings.ACTION_SETTINGS),
    )

    /** Pure: the targets worth starting, in their original order — those [isStartable] accepts. */
    fun <T> startableInOrder(targets: List<T>, isStartable: (T) -> Boolean): List<T> =
        targets.filter { t -> runCatching { isStartable(t) }.getOrDefault(false) }

    /**
     * Opens Accessibility settings, trying [ACCESSIBILITY_TARGETS] in order; each must resolve to an
     * exported activity before it's started. Returns false if nothing could be started (the
     * Settings screen always shows the manual steps either way).
     */
    fun openAccessibilitySettings(context: Context): Boolean {
        val pm = context.packageManager
        val candidates = startableInOrder(ACCESSIBILITY_TARGETS) { t ->
            pm.resolveActivity(intentFor(t), 0)?.activityInfo?.exported == true
        }
        for (target in candidates) {
            try {
                // Don't bounce back to Lounge if Settings briefly shows the stock launcher.
                HomeButtonService.allowStockLauncherUntil(
                    android.os.SystemClock.uptimeMillis() + 3_000,
                )
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

    /**
     * Pure parser for Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES (a ':'-separated list of
     * flattened ComponentNames, long "pkg/pkg.Cls" or short "pkg/.Cls" form).
     */
    fun isServiceInList(enabledList: String?, pkg: String, className: String): Boolean {
        if (enabledList.isNullOrBlank()) return false
        val long = "$pkg/$className"
        val short = if (className.startsWith("$pkg.")) "$pkg/${className.substring(pkg.length)}" else long
        return enabledList.split(':').any { raw ->
            val entry = raw.trim()
            entry.equals(long, ignoreCase = true) || entry.equals(short, ignoreCase = true)
        }
    }
}
