package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.remote.BraviaControl
import ca.cheungeric.lounge.remote.BraviaRpc
import ca.cheungeric.lounge.remote.BraviaRpc.HdmiInput
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** What the Home "Inputs" row shows (SPEC "v6 → HDMI tiles"). */
sealed interface InputsUiState {
    data object Loading : InputsUiState
    data class Ready(val inputs: List<HdmiInput>, val activeUri: String?) : InputsUiState
    data object NotConfigured : InputsUiState
    data object WrongKey : InputsUiState
    data object Unreachable : InputsUiState

    companion object {
        /** Pure mapping from the Bravia call; any error reads as "not reachable" (select retries). */
        fun of(r: BraviaControl.InputsResult): InputsUiState = when (r) {
            is BraviaControl.InputsResult.Ok -> Ready(r.inputs, r.activeUri)
            BraviaControl.InputsResult.NotConfigured -> NotConfigured
            BraviaControl.InputsResult.WrongKey -> WrongKey
            BraviaControl.InputsResult.Unreachable, is BraviaControl.InputsResult.Error -> Unreachable
        }
    }
}

/** HDMI tiles are favourite-tile sized. */
private val InputTile = TileSize.Favorite
private val ConnectedGreen = Color(0xFF4CD97B)
private const val PLACEHOLDER_COUNT = 3

/**
 * Level-2 "Inputs" row: one tile per HDMI input (select switches the TV), or a single status tile.
 * [onSetUp] opens Settings (not configured / wrong key); [onRetry] reloads (unreachable / none).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InputsRow(
    state: InputsUiState,
    onInputClick: (HdmiInput) -> Unit,
    onSetUp: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        RowLabel(stringResource(R.string.home_inputs))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = OverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state) {
                // Placeholders stay focusable so Down still reaches level 2 while the TV answers.
                InputsUiState.Loading -> items(PLACEHOLDER_COUNT) { StatusTile(text = null, onClick = {}) }
                InputsUiState.NotConfigured -> item {
                    StatusTile(stringResource(R.string.inputs_set_up), onClick = onSetUp)
                }
                InputsUiState.WrongKey -> item {
                    StatusTile(stringResource(R.string.inputs_wrong_key), onClick = onSetUp)
                }
                InputsUiState.Unreachable -> item {
                    StatusTile(stringResource(R.string.inputs_unreachable), stringResource(R.string.inputs_retry), onRetry)
                }
                is InputsUiState.Ready -> if (state.inputs.isEmpty()) {
                    item { StatusTile(stringResource(R.string.inputs_none), stringResource(R.string.inputs_retry), onRetry) }
                } else {
                    items(state.inputs, key = { it.uri }) { input ->
                        InputTileView(input, active = input.uri == state.activeUri, onClick = { onInputClick(input) })
                    }
                }
            }
        }
    }
}

@Composable
private fun InputTileView(input: HdmiInput, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hdmi = stringResource(R.string.input_hdmi, input.port)
    val line2 = stringResource(if (input.connected) R.string.input_connected else R.string.input_disconnected, hdmi)
    val chromecast = input.port == BraviaRpc.CHROMECAST_PORT
    TileSurface(
        onClick, focused, interaction, Modifier.semantics { contentDescription = "${input.shownName}, $line2" },
        containerColor = if (chromecast) Color.White else LoungeColors.SurfaceVariant,
    ) {
        // Disconnected inputs are dimmer but still selectable.
        val face = if (chromecast) Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEDEFF3)))) else Modifier
        Box(Modifier.fillMaxSize().then(face)) {
          if (chromecast) GoogleStripe(Modifier.align(Alignment.BottomCenter))
          Box(Modifier.fillMaxSize().alpha(if (input.connected) 1f else 0.55f).padding(16.dp)) {
            if (chromecast) {
                // The Chromecast logo replaces its name and sits in the middle of the card.
                ChromecastLogo(Modifier.align(Alignment.Center).padding(bottom = 14.dp))
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                if (!chromecast) Text(
                    input.shownName,
                    style = MaterialTheme.typography.titleLarge,
                    color = LoungeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    line2,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (chromecast) Color(0xFF5F6368).copy(alpha = 0.9f) else LoungeColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (input.connected) {
                Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(ConnectedGreen, CircleShape))
            }
            if (active) {
                Text(
                    stringResource(R.string.input_now_showing),
                    style = MaterialTheme.typography.labelMedium,
                    color = LoungeColors.OnSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(LoungeColors.PrimaryContainer, LoungeShapes.Pill)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
          }
        }
    }
}

/** Tile-sized status card; [text] null = loading placeholder. */
@Composable
private fun StatusTile(text: String?, subtitle: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val loading = stringResource(R.string.inputs_loading)
    TileSurface(
        onClick,
        focused,
        interaction,
        Modifier.semantics { contentDescription = text ?: loading },
        width = if (text == null) InputTile.width else InputTile.width * 1.6f,
    ) {
        if (text != null) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text(text, style = MaterialTheme.typography.titleMedium, color = LoungeColors.OnSurface, maxLines = 2)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = LoungeColors.OnSurfaceVariant)
                }
            }
        }
    }
}

/** Shared focusable card used by level-2 tiles (inputs, status, history). */
@Composable
internal fun TileSurface(
    onClick: () -> Unit,
    focused: Boolean,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier,
    width: Dp = InputTile.width,
    height: Dp = InputTile.height,
    cornerRadius: Dp = InputTile.cornerRadius,
    containerColor: Color = LoungeColors.SurfaceVariant,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .springFocus(focused, cornerRadius)
            .size(width, height),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(cornerRadius)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = LoungeColors.OnSurface,
            focusedContainerColor = containerColor,
            focusedContentColor = LoungeColors.OnSurface,
            pressedContainerColor = LoungeColors.Outline,
            pressedContentColor = LoungeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        interactionSource = interaction,
    ) { content() }
}

private val LogoRed = Color(0xFFDB4437)
private val LogoYellow = Color(0xFFF4C20D)
private val LogoGreen = Color(0xFF0F9D58)
private val LogoBlue = Color(0xFF4285F4)

/** Thin four-colour Google stripe along the bottom edge of the Chromecast card. */
@Composable
private fun GoogleStripe(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(4.dp)) {
        listOf(LogoBlue, LogoRed, LogoYellow, LogoGreen).forEach { Box(Modifier.weight(1f).fillMaxSize().background(it)) }
    }
}

/** The Chromecast logo: the multicoloured ball plus a light-grey "chromecast" wordmark. */
@Composable
private fun ChromecastLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(40.dp)) {
            val d = size.minDimension
            val box = Size(d, d)
            drawArc(LogoRed, 210f, 120f, true, Offset.Zero, box)
            drawArc(LogoYellow, 330f, 120f, true, Offset.Zero, box)
            drawArc(LogoGreen, 90f, 120f, true, Offset.Zero, box)
            drawCircle(Color.White, d * 0.30f, center)
            drawCircle(LogoBlue, d * 0.23f, center)
        }
        Text("chromecast", fontSize = 24.sp, fontWeight = FontWeight.Light, color = Color(0xFF6E7378), maxLines = 1)
    }
}
