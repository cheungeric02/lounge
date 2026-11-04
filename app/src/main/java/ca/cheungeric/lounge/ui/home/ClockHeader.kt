package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import ca.cheungeric.lounge.ui.theme.LoungeColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** 12-hour clock + date. Ticks exactly on each minute boundary. */
@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMinute(LocalDateTime.now()))
            now = LocalDateTime.now()
        }
    }
    ClockHeaderContent(now, modifier)
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
