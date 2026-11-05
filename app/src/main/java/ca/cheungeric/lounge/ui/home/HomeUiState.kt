package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.data.WeatherCodes
import ca.cheungeric.lounge.data.WeatherIconKey
import kotlin.math.roundToInt

/**
 * Everything HomeScreen renders. Stateless input; T4's ViewModel builds it.
 * The update banner / onboarding tip are passed to [HomeScreen] as its `topContent` slot
 * rather than stored here, so this stays a plain, comparable data class.
 */
data class HomeUiState(
    val home: HomeState,
    val weather: Weather?,
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

/** WMO weather_code -> glyph + short label, derived from the shared [WeatherCodes] mapping. */
internal fun wmoCondition(code: Int, isDay: Boolean): Pair<WeatherGlyph, String> {
    val d = WeatherCodes.describe(code, isDay)
    return glyphOf(d.iconKey) to d.label
}

internal fun glyphOf(key: WeatherIconKey): WeatherGlyph = when (key) {
    WeatherIconKey.CLEAR_DAY -> WeatherGlyph.Sun
    WeatherIconKey.CLEAR_NIGHT -> WeatherGlyph.Moon
    WeatherIconKey.PARTLY_CLOUDY_DAY -> WeatherGlyph.PartlyCloudyDay
    WeatherIconKey.PARTLY_CLOUDY_NIGHT -> WeatherGlyph.PartlyCloudyNight
    WeatherIconKey.CLOUDY -> WeatherGlyph.Cloud
    WeatherIconKey.FOG -> WeatherGlyph.Fog
    WeatherIconKey.DRIZZLE -> WeatherGlyph.Drizzle
    WeatherIconKey.RAIN, WeatherIconKey.SHOWERS -> WeatherGlyph.Rain
    WeatherIconKey.SNOW, WeatherIconKey.SNOW_SHOWERS -> WeatherGlyph.Snow
    WeatherIconKey.THUNDERSTORM -> WeatherGlyph.Thunder
    WeatherIconKey.UNKNOWN -> WeatherGlyph.Cloud
}
