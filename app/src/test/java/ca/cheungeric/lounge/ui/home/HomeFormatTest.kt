package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.data.Weather
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class HomeFormatTest {

    @Test fun clockEvening() {
        val t = formatClock(LocalDateTime.of(2026, 11, 4, 20, 42), Locale.US)
        assertEquals(ClockText("8:42", "pm", "Wednesday, November 4"), t)
    }

    @Test fun clockMidnightAndNoon() {
        assertEquals("12:05", formatClock(LocalDateTime.of(2026, 11, 4, 0, 5), Locale.US).time)
        assertEquals("am", formatClock(LocalDateTime.of(2026, 11, 4, 0, 5), Locale.US).suffix)
        assertEquals("pm", formatClock(LocalDateTime.of(2026, 11, 4, 12, 0), Locale.US).suffix)
    }

    @Test fun nextMinuteDelay() {
        assertEquals(60_000L, millisUntilNextMinute(LocalDateTime.of(2026, 11, 4, 8, 42, 0, 0)))
        assertEquals(500L, millisUntilNextMinute(LocalDateTime.of(2026, 11, 4, 8, 42, 59, 500_000_000)))
        assertEquals(30_000L, millisUntilNextMinute(LocalDateTime.of(2026, 11, 4, 8, 42, 30, 0)))
    }

    @Test fun weatherDisplayRoundsAndFormats() {
        val d = weatherDisplayOf(Weather(temperatureC = 4.5, weatherCode = 0, isDay = true, highC = 7.2, lowC = 0.6))
        assertEquals("5°C", d.temperature)
        assertEquals("Toronto · H 7° L 1°", d.subtitle)
        assertEquals(WeatherGlyph.Sun, d.glyph)
        assertEquals("Clear", d.conditionLabel)
    }

    @Test fun noNegativeZero() {
        assertEquals(0, roundDegrees(-0.4))
        assertEquals("0°C", weatherDisplayOf(Weather(-0.3, 3, true, 1.0, -0.2)).temperature)
        assertEquals(-3, roundDegrees(-2.6))
    }

    @Test fun wmoMapping() {
        assertEquals(WeatherGlyph.Moon, wmoCondition(0, isDay = false).first)
        assertEquals(WeatherGlyph.PartlyCloudyNight, wmoCondition(2, isDay = false).first)
        assertEquals(WeatherGlyph.Cloud, wmoCondition(3, true).first)
        assertEquals(WeatherGlyph.Fog, wmoCondition(48, true).first)
        assertEquals(WeatherGlyph.Drizzle, wmoCondition(53, true).first)
        assertEquals(WeatherGlyph.Rain, wmoCondition(81, true).first)
        assertEquals(WeatherGlyph.Snow, wmoCondition(75, true).first)
        assertEquals(WeatherGlyph.Thunder, wmoCondition(99, true).first)
        assertEquals(WeatherGlyph.Cloud, wmoCondition(1234, true).first)
    }
}
