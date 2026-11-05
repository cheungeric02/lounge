package ca.cheungeric.lounge

import android.content.Context
import ca.cheungeric.lounge.data.AppsRepository
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.MediaHistoryStore
import ca.cheungeric.lounge.data.WeatherRepository
import ca.cheungeric.lounge.remote.BraviaControl
import ca.cheungeric.lounge.remote.RemoteBridge
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

    /** Phone remote (v2); ref-counted by HomeButtonService + MainActivity. */
    val remoteBridge: RemoteBridge by lazy { RemoteBridge(context, appsRepository, layoutPrefs, braviaControl) }

    /** v5: Sony Bravia IP control (power / HDMI input) over the LAN. */
    val braviaControl: BraviaControl by lazy { BraviaControl(context) }

    /** v6: "Continue" / "Recently watched" history recorded from media sessions. */
    val mediaHistory: MediaHistoryStore by lazy { MediaHistoryStore(context) }
}
