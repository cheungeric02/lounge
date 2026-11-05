package ca.cheungeric.lounge.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.ui.home.springFocus
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes

/** What the user picked in the tile action sheet. */
enum class TileAction { MoveLeft, MoveRight, ToggleFavorite, Hide, AppInfo }

/**
 * Hold-OK sheet for one tile (SPEC "Tile action sheet"). Titled with the app label; focus starts on
 * the first row; Back (or Cancel) calls [onDismiss]. The caller closes the sheet after any action.
 */
@Composable
fun TileActionSheet(
    app: AppEntry,
    isFavorite: Boolean,
    onAction: (TileAction) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetFrame(app.label, onDismiss) { first ->
        SheetRow(stringResource(R.string.sheet_move_left), Modifier.focusRequester(first)) {
            onAction(TileAction.MoveLeft)
        }
        SheetRow(stringResource(R.string.sheet_move_right)) { onAction(TileAction.MoveRight) }
        SheetRow(
            stringResource(if (isFavorite) R.string.sheet_remove_favorite else R.string.sheet_add_favorite),
        ) { onAction(TileAction.ToggleFavorite) }
        SheetRow(stringResource(R.string.sheet_hide)) { onAction(TileAction.Hide) }
        SheetRow(stringResource(R.string.sheet_app_info)) { onAction(TileAction.AppInfo) }
        SheetRow(stringResource(R.string.sheet_cancel), onClick = onDismiss)
    }
}

/** v6: hold-OK sheet for a Continue / Recently watched card: "Remove from history" or Cancel. */
@Composable
fun HistoryActionSheet(title: String, onRemove: () -> Unit, onDismiss: () -> Unit) {
    SheetFrame(title, onDismiss) { first ->
        SheetRow(stringResource(R.string.sheet_remove_history), Modifier.focusRequester(first), onClick = onRemove)
        SheetRow(stringResource(R.string.sheet_cancel), onClick = onDismiss)
    }
}

/** Dialog shell shared by the sheets: title, focus on the row given [content]'s requester, OK-swallow. */
@Composable
private fun SheetFrame(title: String, onDismiss: () -> Unit, content: @Composable (FocusRequester) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        // The sheet opens while OK is still held (long-press). Swallow that press's auto-repeats and
        // its key-up so they can't click the first row; arm on the next fresh OK press.
        var armed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (!ok || armed) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        armed = true
                        false
                    } else {
                        true
                    }
                }
                .width(440.dp)
                .background(LoungeColors.Surface, LoungeShapes.LargeShape)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = LoungeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            content(first)
        }
    }
}

private val RowHeight = 52.dp

@Composable
private fun SheetRow(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .springFocus(focused, cornerRadius = LoungeShapes.Medium)
            .height(RowHeight),
        shape = ClickableSurfaceDefaults.shape(LoungeShapes.MediumShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LoungeColors.Surface,
            contentColor = LoungeColors.OnSurface,
            focusedContainerColor = LoungeColors.SurfaceVariant,
            focusedContentColor = LoungeColors.OnSurface,
            pressedContainerColor = LoungeColors.Outline,
            pressedContentColor = LoungeColors.OnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 0.97f),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
