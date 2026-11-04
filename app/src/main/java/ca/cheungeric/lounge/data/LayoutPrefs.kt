package ca.cheungeric.lounge.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class LayoutPrefsState(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val allAppsOrder: List<String> = emptyList(),
    val initialized: Boolean = false,
)

/** DataStore-backed layout preferences. TODO(T2): implement with datastore-preferences. */
class LayoutPrefs(private val context: Context) {
    val state: Flow<LayoutPrefsState> = flowOf(LayoutPrefsState())

    suspend fun update(transform: (LayoutPrefsState) -> LayoutPrefsState) {
        // TODO(T2)
    }
}
