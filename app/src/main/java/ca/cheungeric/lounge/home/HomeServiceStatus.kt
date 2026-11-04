package ca.cheungeric.lounge.home

import android.content.ActivityNotFoundException
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

    /** Opens Accessibility settings, falling back to the main Settings screen. */
    fun openAccessibilitySettings(context: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in tries) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                // Don't bounce back to Lounge if Settings briefly shows the stock launcher.
                HomeButtonService.allowStockLauncherUntil(
                    android.os.SystemClock.uptimeMillis() + 3_000,
                )
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // try next
            } catch (e: SecurityException) {
                // try next
            }
        }
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
