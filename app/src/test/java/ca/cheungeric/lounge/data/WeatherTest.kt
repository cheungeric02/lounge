package ca.cheungeric.lounge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private val sample = """
        {"latitude":43.70455,"longitude":-79.4046,"generationtime_ms":0.05,"utc_offset_seconds":-18000,
         "timezone":"America/Toronto","timezone_abbreviation":"GMT-5","elevation":175.0,
         "current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","is_day":""},
         "current":{"time":"2026-11-04T14:15","interval":900,"temperature_2m":6.4,"weather_code":3,"is_day":1},
         "daily_units":{"time":"iso8601","temperature_2m_max":"°C","temperature_2m_min":"°C"},
         "daily":{"time":["2026-11-04"],"temperature_2m_max":[7.2],"temperature_2m_min":[0.9]}}
    """.trimIndent()

    @Test
    fun parse_realisticSample() {
        val w = WeatherParser.parse(sample)
        assertNotNull(w)
        assertEquals(Weather(6.4, 3, true, 7.2, 0.9), w)
    }

    @Test
    fun parse_nightAndIntegerTemps() {
        val json = sample.replace("\"is_day\":1", "\"is_day\":0").replace("6.4", "-2")
        val w = WeatherParser.parse(json)!!
        assertEquals(false, w.isDay)
        assertEquals(-2.0, w.temperatureC, 0.0)
    }

    @Test
    fun parse_malformedOrIncomplete_returnsNull() {
        assertNull(WeatherParser.parse("not json"))
        assertNull(WeatherParser.parse("{}"))
        assertNull(WeatherParser.parse(sample.replace("[7.2]", "[]")))
        assertNull(WeatherParser.parse(sample.replace("[0.9]", "[null]")))
    }

    @Test
    fun url_matchesSpec() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=43.6532&longitude=-79.3832&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=America%2FToronto&forecast_days=1",
            WeatherRepository.URL_TORONTO,
        )
        assertEquals(30 * 60 * 1000L, WeatherRepository.REFRESH_INTERVAL_MS)
    }

    @Test
    fun wmo_representativeCodes() {
        assertEquals(WeatherIconKey.CLEAR_DAY, WeatherCodes.describe(0, true).iconKey)
        assertEquals(WeatherIconKey.CLEAR_NIGHT, WeatherCodes.describe(0, false).iconKey)
        assertEquals(WeatherIconKey.PARTLY_CLOUDY_DAY, WeatherCodes.describe(2, true).iconKey)
        assertEquals(WeatherIconKey.PARTLY_CLOUDY_NIGHT, WeatherCodes.describe(1, false).iconKey)
        assertEquals(WeatherIconKey.CLOUDY, WeatherCodes.describe(3, true).iconKey)
        assertEquals(WeatherIconKey.FOG, WeatherCodes.describe(45, true).iconKey)
        assertEquals(WeatherIconKey.FOG, WeatherCodes.describe(48, true).iconKey)
        assertEquals(WeatherIconKey.DRIZZLE, WeatherCodes.describe(53, true).iconKey)
        assertEquals(WeatherIconKey.DRIZZLE, WeatherCodes.describe(57, true).iconKey)
        assertEquals(WeatherIconKey.RAIN, WeatherCodes.describe(63, true).iconKey)
        assertEquals(WeatherIconKey.RAIN, WeatherCodes.describe(67, true).iconKey)
        assertEquals(WeatherIconKey.SNOW, WeatherCodes.describe(75, true).iconKey)
        assertEquals(WeatherIconKey.SNOW, WeatherCodes.describe(77, true).iconKey)
        assertEquals(WeatherIconKey.SHOWERS, WeatherCodes.describe(81, true).iconKey)
        assertEquals(WeatherIconKey.SNOW_SHOWERS, WeatherCodes.describe(86, true).iconKey)
        assertEquals(WeatherIconKey.THUNDERSTORM, WeatherCodes.describe(95, true).iconKey)
        assertEquals(WeatherIconKey.THUNDERSTORM, WeatherCodes.describe(99, false).iconKey)
        assertEquals("Cloudy", WeatherCodes.describe(3, true).label)
        assertEquals("Rain", WeatherCodes.describe(61, true).label)
    }

    @Test
    fun wmo_unknownCode_fallsBack() {
        for (code in listOf(-1, 4, 50, 100, 1234)) {
            val d = WeatherCodes.describe(code, true)
            assertEquals(WeatherIconKey.UNKNOWN, d.iconKey)
            assertTrue(d.label.isNotBlank())
        }
    }
}
