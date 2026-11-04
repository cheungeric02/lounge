package ca.cheungeric.lounge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class Weather(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val highC: Double,
    val lowC: Double,
)

/** Icon groups for WMO weather codes; UI maps these to drawables. */
enum class WeatherIconKey {
    CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT, CLOUDY, FOG,
    DRIZZLE, RAIN, SNOW, SHOWERS, SNOW_SHOWERS, THUNDERSTORM, UNKNOWN,
}

data class WeatherDescription(val label: String, val iconKey: WeatherIconKey)

/** Pure WMO weather_code -> short label + icon key. */
object WeatherCodes {
    fun describe(code: Int, isDay: Boolean): WeatherDescription = when (code) {
        0 -> WeatherDescription("Clear", if (isDay) WeatherIconKey.CLEAR_DAY else WeatherIconKey.CLEAR_NIGHT)
        1 -> WeatherDescription("Mostly clear", partly(isDay))
        2 -> WeatherDescription("Partly cloudy", partly(isDay))
        3 -> WeatherDescription("Cloudy", WeatherIconKey.CLOUDY)
        45, 48 -> WeatherDescription("Fog", WeatherIconKey.FOG)
        51, 53, 55 -> WeatherDescription("Drizzle", WeatherIconKey.DRIZZLE)
        56, 57 -> WeatherDescription("Freezing drizzle", WeatherIconKey.DRIZZLE)
        61, 63, 65 -> WeatherDescription("Rain", WeatherIconKey.RAIN)
        66, 67 -> WeatherDescription("Freezing rain", WeatherIconKey.RAIN)
        71, 73, 75, 77 -> WeatherDescription("Snow", WeatherIconKey.SNOW)
        80, 81, 82 -> WeatherDescription("Showers", WeatherIconKey.SHOWERS)
        85, 86 -> WeatherDescription("Snow showers", WeatherIconKey.SNOW_SHOWERS)
        95, 96, 99 -> WeatherDescription("Thunderstorm", WeatherIconKey.THUNDERSTORM)
        else -> WeatherDescription("Weather", WeatherIconKey.UNKNOWN)
    }

    private fun partly(isDay: Boolean) =
        if (isDay) WeatherIconKey.PARTLY_CLOUDY_DAY else WeatherIconKey.PARTLY_CLOUDY_NIGHT
}

/** Pure Open-Meteo JSON parser; returns null on any malformed/incomplete input. */
object WeatherParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Response(val current: Current, val daily: Daily)

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
    )

    @Serializable
    private data class Daily(
        @SerialName("temperature_2m_max") val max: List<Double?>,
        @SerialName("temperature_2m_min") val min: List<Double?>,
    )

    fun parse(body: String): Weather? = try {
        val r = json.decodeFromString(Response.serializer(), body)
        val high = r.daily.max.firstOrNull()
        val low = r.daily.min.firstOrNull()
        if (high == null || low == null) null
        else Weather(r.current.temperature, r.current.weatherCode, r.current.isDay != 0, high, low)
    } catch (_: Exception) {
        null
    }
}

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lounge_weather",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Open-Meteo client (Toronto). Emits cached value on startup; refresh failures keep the last value. */
class WeatherRepository(private val context: Context) {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            val cached = runCatching {
                context.weatherDataStore.data.first()[KEY_CACHE]
            }.getOrNull()?.let(WeatherParser::parse)
            if (cached != null && _weather.value == null) _weather.compareAndSet(null, cached)
        }
    }

    suspend fun refresh() {
        val body = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() } ?: return
        val parsed = WeatherParser.parse(body) ?: return
        _weather.value = parsed
        runCatching { context.weatherDataStore.edit { it[KEY_CACHE] = body } }
    }

    /** Refresh now and every 30 minutes while [scope] is active (e.g. a foreground lifecycle scope). */
    fun startAutoRefresh(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            refresh()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private fun fetch(): String? {
        val conn = URL(URL_TORONTO).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val URL_TORONTO =
            "https://api.open-meteo.com/v1/forecast?latitude=43.6532&longitude=-79.3832" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=America%2FToronto&forecast_days=1"
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val TIMEOUT_MS = 10_000
        private val KEY_CACHE = stringPreferencesKey("last_json")
    }
}
