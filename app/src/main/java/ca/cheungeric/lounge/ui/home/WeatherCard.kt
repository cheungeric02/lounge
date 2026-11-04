package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** Weather pill (not focusable). Renders nothing when [weather] is null (loading / no cache). */
@Composable
fun WeatherCard(weather: Weather?, modifier: Modifier = Modifier) {
    if (weather == null) return
    val display = remember(weather) { weatherDisplayOf(weather) }
    Row(
        modifier = modifier
            .background(LoungeColors.SurfaceVariant, LoungeShapes.Pill)
            .padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(display.glyph, display.conditionLabel, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(display.temperature, style = MaterialTheme.typography.headlineSmall, color = LoungeColors.OnSurface)
            Text(display.subtitle, style = MaterialTheme.typography.bodyLarge, color = LoungeColors.OnSurfaceVariant)
        }
    }
}

/** Round 56dp settings button with gear glyph and spring focus ring. */
@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.home_settings)
    Surface(
        onClick = onClick,
        modifier = modifier
            .springFocus(focused, cornerRadius = null)
            .size(56.dp)
            .semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LoungeColors.SurfaceVariant,
            contentColor = LoungeColors.OnSurface,
            focusedContainerColor = LoungeColors.SurfaceVariant,
            focusedContentColor = LoungeColors.OnSurface,
            pressedContainerColor = LoungeColors.Outline,
            pressedContentColor = LoungeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        interactionSource = interaction,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            GearGlyph(Modifier.size(26.dp), color = if (focused) LoungeColors.FocusRing else LoungeColors.OnSurface)
        }
    }
}
