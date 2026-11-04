package ca.cheungeric.lounge.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Queries PackageManager for launchable apps (LEANBACK_LAUNCHER ∪ LAUNCHER), deduped by package
 * (leanback activity preferred), excluding Lounge itself. Refreshes on package add/remove/change
 * while started (LoungeApp starts it for the Application lifetime).
 */
class AppsRepository(private val context: Context) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    private var receiver: BroadcastReceiver? = null

    /** Re-query installed apps off the main thread. A newer call supersedes an in-flight one. */
    fun refresh() {
        synchronized(this) {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                val result = runCatching { queryApps() }.getOrNull() ?: return@launch
                _apps.value = result
            }
        }
    }

    /** Registers the package-change receiver and does an initial refresh. Idempotent. */
    fun start() {
        synchronized(this) {
            if (receiver == null) {
                val r = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) = refresh()
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addAction(Intent.ACTION_PACKAGE_CHANGED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addDataScheme("package")
                }
                // Package broadcasts are protected (system-only senders), so exported is safe.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(r, filter)
                }
                receiver = r
            }
        }
        refresh()
    }

    fun stop() {
        synchronized(this) {
            receiver?.let { runCatching { context.unregisterReceiver(it) } }
            receiver = null
            refreshJob?.cancel()
        }
    }

    private fun queryApps(): List<AppEntry> {
        val pm = context.packageManager
        val self = context.packageName
        val leanback = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER)
        val launcher = query(pm, Intent.CATEGORY_LAUNCHER)

        val byPkg = LinkedHashMap<String, ResolveInfo>()
        for (ri in leanback + launcher) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == self) continue
            byPkg.putIfAbsent(pkg, ri) // leanback entries come first, so they win
        }

        return byPkg.map { (pkg, ri) -> toEntry(pm, pkg, ri) }
            .sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION") // int-flags overload works on all API levels 28..35
    private fun query(pm: PackageManager, category: String): List<ResolveInfo> =
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.getOrDefault(emptyList())

    private fun toEntry(pm: PackageManager, pkg: String, ri: ResolveInfo): AppEntry {
        val ai = ri.activityInfo
        val label = runCatching { ri.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: pkg
        val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo?.loadBanner(pm) }.getOrNull()
        val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
        val launch = runCatching {
            pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        }.getOrNull()
        return AppEntry(pkg, label, banner, icon, launch)
    }
}
