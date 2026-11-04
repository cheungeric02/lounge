package ca.cheungeric.lounge.ui.home

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** Tile geometry for the two rows. */
data class TileSize(val width: Dp, val height: Dp, val cornerRadius: Dp) {
    companion object {
        val Favorite = TileSize(220.dp, 124.dp, LoungeShapes.Large)
        val AllApps = TileSize(160.dp, 90.dp, LoungeShapes.Medium)
    }
}

/**
 * One app tile: TV banner fill-cropped if the app has one, else its icon (48dp) on a tonal surface
 * with the label beneath. OK -> [onClick]; long-press OK -> [onLongPress] (tv Surface handles D-pad
 * long press). [forceFocused] is for previews only.
 */
@Composable
fun AppTile(
    app: AppEntry,
    tileSize: TileSize,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    forceFocused: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val focused = isFocused || forceFocused
    val shape = RoundedCornerShape(tileSize.cornerRadius)
    val banner = rememberDrawableBitmap(app.packageName, app.banner, 320, 180)
    val icon = if (banner == null) rememberDrawableBitmap(app.packageName, app.icon, 144, 144) else null

    Surface(
        onClick = onClick,
        onLongClick = onLongPress,
        modifier = modifier
            .springFocus(focused, tileSize.cornerRadius)
            .size(tileSize.width, tileSize.height)
            .semantics { contentDescription = app.label },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LoungeColors.SurfaceVariant,
            contentColor = LoungeColors.OnSurface,
            focusedContainerColor = LoungeColors.SurfaceVariant,
            focusedContentColor = LoungeColors.OnSurface,
            pressedContainerColor = LoungeColors.SurfaceVariant,
            pressedContentColor = LoungeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        interactionSource = interaction,
    ) {
        if (banner != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    visible = focused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                ) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = LoungeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(48.dp))
                } else {
                    LetterBadge(app.label)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = LoungeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Fallback when an app has neither banner nor icon. */
@Composable
private fun LetterBadge(label: String) {
    Box(
        Modifier.size(48.dp).background(LoungeColors.PrimaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.trim().take(1).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = LoungeColors.OnSurface,
        )
    }
}

/** Drawable -> ImageBitmap, cached per package; falls back to [fallbackW]x[fallbackH] px when intrinsic size is unknown. */
@Composable
private fun rememberDrawableBitmap(key: String, drawable: Drawable?, fallbackW: Int, fallbackH: Int): ImageBitmap? =
    remember(key, drawable) {
        drawable?.let {
            runCatching {
                val w = if (it.intrinsicWidth > 0) it.intrinsicWidth else fallbackW
                val h = if (it.intrinsicHeight > 0) it.intrinsicHeight else fallbackH
                it.toBitmap(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
