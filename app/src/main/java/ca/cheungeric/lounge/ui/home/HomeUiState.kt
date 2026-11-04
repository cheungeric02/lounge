package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.data.Weather
import kotlin.math.roundToInt

/**
 * Everything HomeScreen renders. Stateless input; T4's ViewModel builds it.
 * The update banner / onboarding tip are passed to [HomeScreen] as its `topContent` slot
 * rather than stored here, so this stays a plain, comparable data class.
 */
data class HomeUiState(
    val home: HomeState,
    val weather: Weather?,
    val showContinueWatching: Boolean = false,
)

/** Condition glyph drawn by WeatherIcon. */
enum class WeatherGlyph { Sun, Moon, PartlyCloudyDay, PartlyCloudyNight, Cloud, Fog, Drizzle, Rain, Snow, Thunder }

/** Display model derived from [Weather]: "7°C" + "Toronto · H 7° L 1°". */
data class WeatherDisplay(
    val temperature: String,
    val subtitle: String,
    val glyph: WeatherGlyph,
    val conditionLabel: String,
)

/** Rounds to the nearest whole degree, never showing "-0". */
internal fun roundDegrees(c: Double): Int {
    val r = c.roundToInt()
    return if (r == 0) 0 else r
}

internal fun weatherDisplayOf(weather: Weather, location: String = "Toronto"): WeatherDisplay {
    val (glyph, label) = wmoCondition(weather.weatherCode, weather.isDay)
    return WeatherDisplay(
        temperature = "${roundDegrees(weather.temperatureC)}°C",
        subtitle = "$location · H ${roundDegrees(weather.highC)}° L ${roundDegrees(weather.lowC)}°",
        glyph = glyph,
        conditionLabel = label,
    )
}

/**
 * Private WMO weather_code -> glyph + short label mapping for the Home pill.
 * T2 adds a shared mapping in data/; T4 may consolidate onto that one.
 */
internal fun wmoCondition(code: Int, isDay: Boolean): Pair<WeatherGlyph, String> = when (code) {
    0 -> (if (isDay) WeatherGlyph.Sun else WeatherGlyph.Moon) to "Clear"
    1 -> (if (isDay) WeatherGlyph.Sun else WeatherGlyph.Moon) to "Mostly clear"
    2 -> (if (isDay) WeatherGlyph.PartlyCloudyDay else WeatherGlyph.PartlyCloudyNight) to "Partly cloudy"
    3 -> WeatherGlyph.Cloud to "Cloudy"
    45, 48 -> WeatherGlyph.Fog to "Fog"
    51, 53, 55 -> WeatherGlyph.Drizzle to "Drizzle"
    56, 57 -> WeatherGlyph.Drizzle to "Freezing drizzle"
    61, 63, 65 -> WeatherGlyph.Rain to "Rain"
    66, 67 -> WeatherGlyph.Rain to "Freezing rain"
    71, 73, 75, 77 -> WeatherGlyph.Snow to "Snow"
    80, 81, 82 -> WeatherGlyph.Rain to "Showers"
    85, 86 -> WeatherGlyph.Snow to "Snow showers"
    95, 96, 99 -> WeatherGlyph.Thunder to "Thunderstorm"
    else -> WeatherGlyph.Cloud to "Cloudy"
}
