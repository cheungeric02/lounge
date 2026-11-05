package ca.cheungeric.lounge.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeFocus

/** Ring colour for [springFocus]; Home provides the focused app's tint (v6), elsewhere the theme ring. */
internal val LocalFocusRingColor = compositionLocalOf { LoungeColors.FocusRing }

/**
 * M3-Expressive spring focus: scale 1.0 -> 1.1 (dampingRatio 0.6, stiffness medium-low) and a
 * 3dp FocusRing outline drawn 3dp outside the element's bounds. [cornerRadius] is the element's
 * own corner radius; pass null for a circle. Focused element draws above its neighbours.
 */
internal fun Modifier.springFocus(focused: Boolean, cornerRadius: Dp?): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (focused) LoungeFocus.FocusedScale else 1f,
        animationSpec = spring(dampingRatio = LoungeFocus.SpringDamping, stiffness = Spring.StiffnessMediumLow),
        label = "focusScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focusRing",
    )
    val ringColor: Color = LocalFocusRingColor.current
    this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (ringAlpha > 0f) {
                val stroke = LoungeFocus.RingWidth.toPx()
                // Centre of the stroke sits offset + stroke/2 outside the bounds.
                val out = LoungeFocus.RingOffset.toPx() + stroke / 2f
                val r = if (cornerRadius == null) size.minDimension / 2f + out else cornerRadius.toPx() + out
                drawRoundRect(
                    color = ringColor,
                    topLeft = Offset(-out, -out),
                    size = Size(size.width + out * 2, size.height + out * 2),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(width = stroke),
                    alpha = ringAlpha,
                )
            }
        }
}
