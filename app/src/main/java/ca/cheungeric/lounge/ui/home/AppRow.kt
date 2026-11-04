package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.ui.theme.LoungeColors

/** Overscan-safe side padding; rows scroll edge-to-edge but start/end at this inset. */
internal val OverscanHorizontal = 48.dp
internal val OverscanVertical = 27.dp
private val TileGap = 20.dp

/**
 * Labelled horizontal row of [AppTile]s. Re-entering the row returns focus to the last focused
 * tile ([focusRestorer]). [firstItemFocusRequester] is attached to tile 0 (initial focus);
 * [upTarget], if set, is where D-pad Up goes from any tile in this row. When [apps] is empty and
 * [emptyHint] is set, a single non-focusable hint card is shown instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRow(
    title: String,
    apps: List<AppEntry>,
    tileSize: TileSize,
    onAppClick: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
    upTarget: FocusRequester? = null,
    emptyHint: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        RowLabel(title)
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) {
            if (emptyHint != null) HintCard(emptyHint, tileSize, Modifier.padding(horizontal = OverscanHorizontal))
            return@Column
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = OverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                var tileModifier: Modifier = Modifier
                if (index == 0 && firstItemFocusRequester != null) {
                    tileModifier = tileModifier.focusRequester(firstItemFocusRequester)
                }
                if (upTarget != null) {
                    tileModifier = tileModifier.focusProperties { up = upTarget }
                }
                AppTile(
                    app = app,
                    tileSize = tileSize,
                    onClick = { onAppClick(app) },
                    onLongPress = { onAppLongPress(app) },
                    modifier = tileModifier,
                )
            }
        }
    }
}

@Composable
internal fun RowLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = LoungeColors.OnSurfaceVariant,
        modifier = modifier.padding(horizontal = OverscanHorizontal),
    )
}

/** Non-focusable tile-sized hint (used for the empty favorites row). */
@Composable
private fun HintCard(text: String, tileSize: TileSize, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(tileSize.width * 2 + TileGap, tileSize.height)
            .background(LoungeColors.Surface, RoundedCornerShape(tileSize.cornerRadius))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = LoungeColors.TertiaryText,
            textAlign = TextAlign.Center,
        )
    }
}
