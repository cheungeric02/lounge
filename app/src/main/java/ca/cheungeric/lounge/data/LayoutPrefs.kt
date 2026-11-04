package ca.cheungeric.lounge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

data class LayoutPrefsState(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val allAppsOrder: List<String> = emptyList(),
    val initialized: Boolean = false,
)

private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lounge_layout",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** JSON string-list codec for prefs; corrupt input decodes to an empty list, never throws. */
internal object StringListCodec {
    private val serializer = ListSerializer(String.serializer())

    fun encode(list: List<String>): String = Json.encodeToString(serializer, list)

    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Json.decodeFromString(serializer, json)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** DataStore-backed layout preferences ("lounge_layout"). */
class LayoutPrefs(private val context: Context) {
    private val store get() = context.layoutDataStore

    val state: Flow<LayoutPrefsState> = context.layoutDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toState() }
        .distinctUntilChanged()

    suspend fun update(transform: (LayoutPrefsState) -> LayoutPrefsState) {
        store.edit { prefs ->
            val next = transform(prefs.toState())
            prefs[KEY_FAVORITES] = StringListCodec.encode(next.favorites)
            prefs[KEY_ALL_APPS_ORDER] = StringListCodec.encode(next.allAppsOrder)
            prefs[KEY_HIDDEN] = next.hidden
            prefs[KEY_INITIALIZED] = next.initialized
        }
    }

    private fun Preferences.toState() = LayoutPrefsState(
        favorites = StringListCodec.decode(this[KEY_FAVORITES]),
        hidden = this[KEY_HIDDEN] ?: emptySet(),
        allAppsOrder = StringListCodec.decode(this[KEY_ALL_APPS_ORDER]),
        initialized = this[KEY_INITIALIZED] ?: false,
    )

    private companion object {
        val KEY_FAVORITES = stringPreferencesKey("favorites")
        val KEY_ALL_APPS_ORDER = stringPreferencesKey("allAppsOrder")
        val KEY_HIDDEN = stringSetPreferencesKey("hidden")
        val KEY_INITIALIZED = booleanPreferencesKey("initialized")
    }
}
