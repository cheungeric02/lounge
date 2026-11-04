package ca.cheungeric.lounge.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Design tokens from docs/SPEC.md "Theme tokens" (dark only). */
object LoungeColors {
    val Background = Color(0xFF14121A)
    val Surface = Color(0xFF1E1A24)
    val SurfaceVariant = Color(0xFF2A2433)
    val Primary = Color(0xFFD9C2FF)
    val PrimaryContainer = Color(0xFF6B4FA8)
    val OnSurface = Color(0xFFE8E3EF)
    val OnSurfaceVariant = Color(0xFFB9AFCB)
    val Outline = Color(0xFF4A4256)
    val TertiaryText = Color(0xFF8E849E)
    val FocusRing = Color(0xFFF2EAFF)
}

object LoungeShapes {
    val Large = 20.dp
    val Medium = 16.dp
}

private val LoungeColorScheme = darkColorScheme(
    primary = LoungeColors.Primary,
    primaryContainer = LoungeColors.PrimaryContainer,
    background = LoungeColors.Background,
    surface = LoungeColors.Surface,
    surfaceVariant = LoungeColors.SurfaceVariant,
    onSurface = LoungeColors.OnSurface,
    onSurfaceVariant = LoungeColors.OnSurfaceVariant,
    onBackground = LoungeColors.OnSurface,
    border = LoungeColors.Outline,
)

@Composable
fun LoungeTheme(content: @Composable () -> Unit) {
    // TODO(T3): typography + shapes per SPEC.
    MaterialTheme(colorScheme = LoungeColorScheme, content = content)
}
