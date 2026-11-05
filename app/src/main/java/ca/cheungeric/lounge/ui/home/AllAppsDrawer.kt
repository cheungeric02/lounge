package ca.cheungeric.lounge.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.ui.background.BgImage
import ca.cheungeric.lounge.ui.background.WebbBackdrop
import ca.cheungeric.lounge.ui.theme.LoungeColors

/** Every visible app (favourites + the rest) A→Z, case-insensitive; hidden apps are never in [home]. */
fun drawerApps(home: HomeState): List<AppEntry> =
    (home.favorites + home.others).distinctBy { it.packageName }.sortedBy { it.label.lowercase() }

private const val DRAWER_COLUMNS = 6
/** The drawer's scrim is this much darker than Home's (capped). */
private const val DRAWER_EXTRA_DIM = 20
private val GridGap = 16.dp

/** Last tile of the dock: a dots glyph that opens the All apps drawer. Favourite-tile sized. */
@Composable
fun AllAppsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val label = stringResource(R.string.home_all_apps)
    TileSurface(
        onClick, focused, interaction,
        modifier = modifier.semantics { contentDescription = label },
        width = TileSize.Favorite.width, height = TileSize.Favorite.height, cornerRadius = TileSize.Favorite.cornerRadius,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DotsGlyph(Modifier.size(32.dp), color = if (focused) LoungeColors.FocusRing else LoungeColors.OnSurface)
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = LoungeColors.OnSurface)
        }
    }
}

/**
 * Full-screen All apps overlay (6-column grid). Focus starts on the first tile and is trapped inside
 * while open; Back calls [onClose] (the caller returns focus to the All apps tile).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AllAppsDrawer(
    apps: List<AppEntry>,
    image: BgImage,
    dim: Int,
    onAppClick: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    Box(
        modifier
            .fillMaxSize()
            // Keep D-pad focus inside the drawer; Home stays composed underneath.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) {
        WebbBackdrop(image, dim = (dim + DRAWER_EXTRA_DIM).coerceAtMost(90), modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(top = OverscanVertical)) {
            Text(
                stringResource(R.string.home_all_apps),
                style = MaterialTheme.typography.headlineMedium,
                color = LoungeColors.OnSurface,
                modifier = Modifier.padding(horizontal = OverscanHorizontal),
            )
            Spacer(Modifier.height(20.dp))
            // 6 × 160dp tiles don't fit a 960dp-wide TV inside overscan, so tiles shrink to the cell
            // width (keeping TileSize.AllApps' 16:9 shape and corner) when needed.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cell = (maxWidth - OverscanHorizontal * 2 - GridGap * (DRAWER_COLUMNS - 1)) / DRAWER_COLUMNS
                val w = minOf(TileSize.AllApps.width, cell)
                val tileSize = TileSize(w, TileSize.AllApps.height * (w / TileSize.AllApps.width), TileSize.AllApps.cornerRadius)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(DRAWER_COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = OverscanHorizontal, end = OverscanHorizontal, top = 8.dp, bottom = OverscanVertical,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        Box(contentAlignment = Alignment.Center) {
                            AppTile(
                                app = app,
                                tileSize = tileSize,
                                onClick = { onAppClick(app) },
                                onLongPress = { onAppLongPress(app) },
                                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
