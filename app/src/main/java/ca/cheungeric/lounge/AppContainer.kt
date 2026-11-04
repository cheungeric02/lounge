package ca.cheungeric.lounge

import android.content.Context
import ca.cheungeric.lounge.data.AppsRepository
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.WeatherRepository

/** Manual DI (no Hilt). TODO(T4/T6): update/ wiring. */
class AppContainer(val context: Context) {
    val appsRepository: AppsRepository by lazy { AppsRepository(context) }
    val layoutPrefs: LayoutPrefs by lazy { LayoutPrefs(context) }
    val weatherRepository: WeatherRepository by lazy { WeatherRepository(context) }
}
