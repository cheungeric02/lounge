package ca.cheungeric.lounge.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Weather(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val highC: Double,
    val lowC: Double,
)

/** Open-Meteo client (Toronto). TODO(T2): fetch, parse, cache, 30-min refresh. */
class WeatherRepository(private val context: Context) {
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    suspend fun refresh() {
        // TODO(T2)
    }
}
