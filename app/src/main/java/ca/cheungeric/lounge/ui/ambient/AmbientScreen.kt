package ca.cheungeric.lounge.ui.ambient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.ui.home.formatClock
import ca.cheungeric.lounge.ui.home.millisUntilNextMinute
import ca.cheungeric.lounge.ui.home.weatherDisplayOf
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Dim clock grey: few lit pixels on OLED / low backlight on LCD. */
private val AmbientPrimary = Color(0xFF5A5A5A)
private val AmbientSecondary = Color(0xFF404040)

/**
 * Pure black (#000) idle screen: dim 12-hour clock, date and weather. Recomposes once a minute
 * (minute-aligned) and moves the block to a new pseudo-random spot each minute (burn-in shift).
 * No animations, images or gradients.
 */
@Composable
fun AmbientScreen(weather: Weather?, modifier: Modifier = Modifier) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMinute(LocalDateTime.now()))
            nowMs = System.currentTimeMillis()
        }
    }
    val locales = LocalConfiguration.current.locales
    val locale = if (locales.isEmpty) java.util.Locale.getDefault() else locales[0]
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
    val clock = remember(nowMs / 60_000L, locale) { formatClock(now, locale) }
    val weatherLine = remember(weather) {
        weather?.let { val d = weatherDisplayOf(it); "${d.temperature} · ${d.conditionLabel}" }
    }
    val minuteIndex = nowMs / 60_000L

    Layout(
        content = {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(clock.time, style = MaterialTheme.typography.displayLarge, color = AmbientPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        clock.suffix,
                        style = MaterialTheme.typography.headlineMedium,
                        color = AmbientSecondary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Text(clock.date, style = MaterialTheme.typography.titleLarge, color = AmbientSecondary)
                if (weatherLine != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(weatherLine, style = MaterialTheme.typography.titleLarge, color = AmbientSecondary)
                }
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val marginX = (w * AmbientLayout.SAFE_MARGIN_FRACTION).roundToInt()
        val marginY = (h * AmbientLayout.SAFE_MARGIN_FRACTION).roundToInt()
        val block = measurables.first().measure(Constraints(maxWidth = w, maxHeight = h))
        val off = AmbientLayout.offsetFor(minuteIndex, w - 2 * marginX - block.width, h - 2 * marginY - block.height)
        layout(w, h) {
            block.place(marginX + off.x, marginY + off.y)
        }
    }
}
