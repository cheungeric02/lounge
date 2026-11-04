package ca.cheungeric.lounge

import android.content.Context
import ca.cheungeric.lounge.data.AppsRepository
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.WeatherRepository
import ca.cheungeric.lounge.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI (no Hilt). */
class AppContainer(val context: Context) {
    /** Process-lifetime scope for app-wide work (self-update). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val appsRepository: AppsRepository by lazy { AppsRepository(context) }
    val layoutPrefs: LayoutPrefs by lazy { LayoutPrefs(context) }
    val weatherRepository: WeatherRepository by lazy { WeatherRepository(context) }
    val updateManager: UpdateManager by lazy { UpdateManager(context, appScope) }
}
