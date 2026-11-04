package ca.cheungeric.lounge.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.cheungeric.lounge.ui.home.springFocus

/** Pill button height; the spring focus ring follows a radius of half of it. */
private val ButtonHeight = 48.dp

/**
 * Focusable pill button with the shared spring focus (ring + 1.1x). [primary] uses the
 * primaryContainer tone, otherwise the tonal surfaceVariant.
 */
@Composable
fun LoungeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container = if (primary) LoungeColors.PrimaryContainer else LoungeColors.SurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier
            .springFocus(focused, cornerRadius = ButtonHeight / 2)
            .heightIn(min = ButtonHeight),
        shape = ClickableSurfaceDefaults.shape(LoungeShapes.Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = LoungeColors.OnSurface,
            focusedContainerColor = container,
            focusedContentColor = LoungeColors.OnSurface,
            pressedContainerColor = LoungeColors.Outline,
            pressedContentColor = LoungeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.95f),
        interactionSource = interaction,
    ) {
        Box(Modifier.heightIn(min = ButtonHeight).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
        }
    }
}
