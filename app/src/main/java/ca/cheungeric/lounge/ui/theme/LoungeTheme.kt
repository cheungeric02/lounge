package ca.cheungeric.lounge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import androidx.tv.material3.Typography
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

/** Shape tokens: large 20dp, medium 16dp, pill 50%. */
object LoungeShapes {
    val Large = 20.dp
    val Medium = 16.dp
    val LargeShape = RoundedCornerShape(Large)
    val MediumShape = RoundedCornerShape(Medium)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Focus tokens shared by every focusable on Home: 3dp ring, 3dp gap, 1.1x spring scale. */
object LoungeFocus {
    val RingWidth = 3.dp
    val RingOffset = 3.dp
    const val FocusedScale = 1.1f
    const val SpringDamping = 0.6f
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

/** Clamp every weight to the two allowed by SPEC: 400 (Normal) and 500 (Medium). */
private fun TextStyle.twoWeights(): TextStyle {
    val w = fontWeight?.weight ?: 400
    return copy(fontWeight = if (w >= 500) FontWeight.Medium else FontWeight.Normal)
}

private fun style(size: Int, weight: FontWeight, lineHeight: Int = (size * 1.25f).toInt()) =
    TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.sp)

private val LoungeTypography: Typography = Typography().let { d ->
    Typography(
        displayLarge = style(72, FontWeight.Medium, lineHeight = 80), // clock
        displayMedium = d.displayMedium.twoWeights(),
        displaySmall = d.displaySmall.twoWeights(),
        headlineLarge = d.headlineLarge.twoWeights(),
        headlineMedium = style(28, FontWeight.Normal), // am/pm suffix
        headlineSmall = style(26, FontWeight.Medium), // weather temp
        titleLarge = style(20, FontWeight.Normal), // date
        titleMedium = style(18, FontWeight.Medium), // row labels
        titleSmall = d.titleSmall.twoWeights(),
        bodyLarge = style(15, FontWeight.Normal), // weather subtitle
        bodyMedium = style(14, FontWeight.Normal),
        bodySmall = style(13, FontWeight.Normal), // footer hint
        labelLarge = style(14, FontWeight.Medium), // tile labels
        labelMedium = d.labelMedium.twoWeights(),
        labelSmall = d.labelSmall.twoWeights(),
    )
}

private val LoungeMaterialShapes = Shapes(
    medium = LoungeShapes.MediumShape,
    large = LoungeShapes.LargeShape,
)

@Composable
fun LoungeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoungeColorScheme,
        typography = LoungeTypography,
        shapes = LoungeMaterialShapes,
        content = content,
    )
}
