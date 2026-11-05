package ca.cheungeric.lounge.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HistoryEntry
import ca.cheungeric.lounge.data.MediaHistory
import ca.cheungeric.lounge.ui.theme.LoungeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One history card: the entry, its saved art (absolute path) and the installed app (icon fallback). */
data class HomeHistoryItem(
    val entry: HistoryEntry,
    val artPath: String? = null,
    val app: AppEntry? = null,
)

/** Continue card ≈ 1.6× a favourite; Recently watched cards are a little smaller than favourites. */
internal val ContinueCardWidth = TileSize.Favorite.width * 1.6f
private val RecentCard = TileSize(200.dp, 112.dp, TileSize.AllApps.cornerRadius)

/** Level-1 Continue card (left of favourites). OK resumes the app; hold-OK → remove from history. */
@Composable
fun ContinueCard(
    item: HomeHistoryItem,
    nowMs: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HistoryCard(
        item, nowMs, onClick, onLongPress, modifier,
        width = ContinueCardWidth, height = TileSize.Favorite.height, cornerRadius = TileSize.Favorite.cornerRadius,
        label = stringResource(R.string.home_continue_watching),
    )
}

/**
 * Level-2 "Recently watched" row. With no history: a hint card that opens Notification access when
 * access is off, otherwise nothing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RecentlyWatchedRow(
    items: List<HomeHistoryItem>,
    mediaAccessGranted: Boolean,
    nowMs: Long,
    onClick: (HomeHistoryItem) -> Unit,
    onLongPress: (HomeHistoryItem) -> Unit,
    onOpenMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty() && mediaAccessGranted) return
    Column(modifier.fillMaxWidth()) {
        RowLabel(stringResource(R.string.home_recently_watched))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = OverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (items.isEmpty()) {
                item { AccessHintCard(onOpenMediaAccess) }
            } else {
                items(items, key = { it.entry.packageName + "\u0000" + it.entry.title }) { item ->
                    HistoryCard(
                        item, nowMs, onClick = { onClick(item) }, onLongPress = { onLongPress(item) },
                        width = RecentCard.width, height = RecentCard.height, cornerRadius = RecentCard.cornerRadius,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessHintCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    TileSurface(
        onClick, focused, interaction,
        width = RecentCard.width * 2 + 20.dp, height = RecentCard.height, cornerRadius = RecentCard.cornerRadius,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                stringResource(R.string.home_history_access_hint),
                style = MaterialTheme.typography.titleMedium,
                color = LoungeColors.OnSurface,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: HomeHistoryItem,
    nowMs: Long,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    label: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val e = item.entry
    val meta = stringResource(R.string.home_history_meta, e.appLabel, MediaHistory.relativeAge(nowMs, e.updatedAtMs))
    val art = rememberFileBitmap(item.artPath)
    val banner = if (art == null) rememberDrawableBitmap(e.packageName, item.app?.banner, 320, 180) else null
    val icon = if (art == null && banner == null) rememberDrawableBitmap(e.packageName, item.app?.icon, 144, 144) else null
    val description = listOfNotNull(label, e.title, meta).joinToString(", ")
    TileSurface(
        onClick, focused, interaction,
        modifier = modifier.semantics { contentDescription = description },
        width = width, height = height, cornerRadius = cornerRadius,
        onLongClick = onLongPress,
    ) {
        Box(Modifier.fillMaxSize()) {
            val picture = art ?: banner
            if (picture != null) {
                Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (icon != null) {
                Image(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp),
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                    .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            ) {
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = LoungeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = LoungeColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Decodes a small saved art PNG off the main thread; null while loading / missing. */
@Composable
private fun rememberFileBitmap(path: String?): ImageBitmap? {
    val bmp = remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path != null) {
            bmp.value = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return bmp.value
}
