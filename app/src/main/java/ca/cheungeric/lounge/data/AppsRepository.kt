package ca.cheungeric.lounge.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Queries PackageManager for launchable apps. TODO(T2): implement + package change receiver. */
class AppsRepository(private val context: Context) {
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    fun refresh() {
        // TODO(T2)
    }
}
