package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.ui.theme.LoungeColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** 12-hour clock + date. Ticks exactly on each minute boundary. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    ClockHeaderContent(rememberMinuteClock(), modifier)
}

/** v6 Home: the clock ~1.3× displayLarge, the date, then a one-line weather summary (if any). */
@Composable
fun BigClockHeader(weather: Weather?, modifier: Modifier = Modifier) {
    BigClockHeaderContent(rememberMinuteClock(), weather, modifier)
}

@Composable
private fun rememberMinuteClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMinute(LocalDateTime.now()))
            now = LocalDateTime.now()
        }
    }
    return now
}

private const val BIG_CLOCK_SCALE = 1.3f

@Composable
internal fun BigClockHeaderContent(now: LocalDateTime, weather: Weather?, modifier: Modifier = Modifier) {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) java.util.Locale.getDefault() else locales[0]
    val text = remember(now, locale) { formatClock(now, locale) }
    val base = MaterialTheme.typography.displayLarge
    val big = base.copy(fontSize = base.fontSize * BIG_CLOCK_SCALE, lineHeight = base.lineHeight * BIG_CLOCK_SCALE)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text.time, style = big, color = LoungeColors.OnSurface)
            Spacer(Modifier.width(10.dp))
            Text(
                text.suffix,
                style = MaterialTheme.typography.headlineMedium,
                color = LoungeColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Text(text.date, style = MaterialTheme.typography.titleLarge, color = LoungeColors.OnSurfaceVariant)
        if (weather != null) {
            val w = remember(weather) { weatherDisplayOf(weather) }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(w.glyph, w.conditionLabel, Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "${w.temperature} · ${w.conditionLabel} · ${w.subtitle}",
                    style = MaterialTheme.typography.titleMedium,
                    color = LoungeColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ClockHeaderContent(now: LocalDateTime, modifier: Modifier = Modifier) {
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) java.util.Locale.getDefault() else locales[0]
    val text = remember(now, locale) { formatClock(now, locale) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text.time, style = MaterialTheme.typography.displayLarge, color = LoungeColors.OnSurface)
            Spacer(Modifier.width(8.dp))
            Text(
                text.suffix,
                style = MaterialTheme.typography.headlineMedium,
                color = LoungeColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(text.date, style = MaterialTheme.typography.titleLarge, color = LoungeColors.OnSurfaceVariant)
    }
}
