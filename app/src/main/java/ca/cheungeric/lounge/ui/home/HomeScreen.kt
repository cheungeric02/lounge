package ca.cheungeric.lounge.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.remote.BraviaRpc.HdmiInput
import ca.cheungeric.lounge.ui.background.BackgroundSettings
import ca.cheungeric.lounge.ui.background.WebbBackdrop
import ca.cheungeric.lounge.ui.theme.LoungeColors
import kotlinx.coroutines.launch

/** Level-2 (Down) content and callbacks; Home works without it (previews). */
data class HomeLevel2(
    val inputs: InputsUiState = InputsUiState.Loading,
    val history: List<HomeHistoryItem> = emptyList(),
    val mediaAccessGranted: Boolean = true,
    val nowMs: Long = 0L,
    val onInputClick: (HdmiInput) -> Unit = {},
    val onInputsSetUp: () -> Unit = {},
    val onInputsRetry: () -> Unit = {},
    val onHistoryClick: (HomeHistoryItem) -> Unit = {},
    val onHistoryLongPress: (HomeHistoryItem) -> Unit = {},
    val onOpenMediaAccess: () -> Unit = {},
    val onFocused: (Boolean) -> Unit = {},
)

/** How strongly the focus ring leans toward the app tint (keeps it light enough to read on photos). */
private const val RING_TINT = 0.55f
private const val TINT_FADE_MS = 600

/**
 * Stateless v6 "Minimal dock" Home (SPEC "v6"). Level 1 fills the screen: big clock / date / weather,
 * now-playing + settings top-right, then one dock row [Continue] + favourites + All apps. Down pages
 * to level 2 (HDMI inputs, Recently watched). [topContent] (update banner / tip) sits above the dock.
 * Initial focus: first favourite, else the All apps tile; Up from the dock reaches settings.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAppClick: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topContent: (@Composable () -> Unit)? = null,
    nowPlaying: HomeNowPlaying? = null,
    background: BackgroundSettings = BackgroundSettings(),
    focusTint: Color? = null,
    onAppFocused: (AppEntry?) -> Unit = {},
    level2: HomeLevel2 = HomeLevel2(),
    allAppsOpen: Boolean = false,
    onOpenAllApps: () -> Unit = {},
    onCloseAllApps: () -> Unit = {},
) {
    val favorites = state.home.favorites
    val others = state.home.others
    val continueItem = level2.history.firstOrNull()
    val settingsFocus = remember { FocusRequester() }
    val favoritesFocus = remember { FocusRequester() }
    val allAppsFocus = remember { FocusRequester() }

    // Initial focus: once, on the first favourite; the All apps tile only counts as "done" once the
    // app list has loaded (favourites may still arrive).
    var initialFocusDone by remember { mutableStateOf(false) }
    val toFavorites = favorites.isNotEmpty()
    LaunchedEffect(toFavorites, others.isNotEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        withFrameNanos { }
        val ok = runCatching { (if (toFavorites) favoritesFocus else allAppsFocus).requestFocus() }.isSuccess
        if (ok && (toFavorites || others.isNotEmpty())) initialFocusDone = true
    }

    // Closing the drawer returns focus to the All apps tile.
    var drawerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(allAppsOpen) {
        if (allAppsOpen) {
            drawerWasOpen = true
        } else if (drawerWasOpen) {
            drawerWasOpen = false
            withFrameNanos { }
            runCatching { allAppsFocus.requestFocus() }
        }
    }

    val tint by animateColorAsState(
        targetValue = focusTint ?: LoungeColors.Primary,
        animationSpec = tween(TINT_FADE_MS),
        label = "focusTint",
    )
    val ringColor = lerp(LoungeColors.FocusRing, tint, RING_TINT)
    val upToSettings = if (topContent == null) settingsFocus else null
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Which level holds focus (1 / 2 / 0 = neither); acted on only when it changes.
    var focusedLevel by remember { mutableIntStateOf(0) }
    fun onLevelFocus(level: Int, has: Boolean) {
        val next = if (has) level else if (focusedLevel == level) 0 else focusedLevel
        if (next == focusedLevel) return
        val wasLevel2 = focusedLevel == 2
        focusedLevel = next
        if (next == 2) level2.onFocused(true) else if (wasLevel2) level2.onFocused(false)
        when (next) {
            1 -> scope.launch { scroll.animateScrollTo(0) }
            2 -> scope.launch { scroll.animateScrollTo(scroll.maxValue) }
        }
    }

    // Leaving Home (e.g. to Settings) while on level 2 stops the inputs polling.
    val onLevel2 by rememberUpdatedState(level2.onFocused)
    DisposableEffect(Unit) { onDispose { if (focusedLevel == 2) onLevel2(false) } }

    Box(modifier.fillMaxSize()) {
        WebbBackdrop(background.image, background.dim, Modifier.fillMaxSize(), glow = tint)
        CompositionLocalProvider(LocalFocusRingColor provides ringColor) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val page = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    // ---- Level 1 ----
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(page)
                            .onFocusChanged { onLevelFocus(1, it.hasFocus) }
                            .padding(vertical = OverscanVertical),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = OverscanHorizontal)) {
                            BigClockHeader(state.weather)
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (nowPlaying != null) {
                                    NowPlayingPill(nowPlaying, Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(16.dp))
                                }
                                SettingsButton(
                                    onClick = onSettingsClick,
                                    modifier = Modifier
                                        .focusRequester(settingsFocus)
                                        .onFocusChanged { if (it.isFocused) onAppFocused(null) },
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (topContent != null) {
                            Box(Modifier.padding(horizontal = OverscanHorizontal)) { topContent() }
                            Spacer(Modifier.height(20.dp))
                        }
                        if (favorites.isEmpty() && others.isEmpty()) {
                            Text(
                                stringResource(R.string.home_no_apps),
                                style = MaterialTheme.typography.titleMedium,
                                color = LoungeColors.TertiaryText,
                                modifier = Modifier.padding(horizontal = OverscanHorizontal, vertical = 8.dp),
                            )
                        }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            contentPadding = PaddingValues(horizontal = OverscanHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            fun Modifier.dock(app: AppEntry?): Modifier =
                                (if (upToSettings != null) focusProperties { up = upToSettings } else this)
                                    .onFocusChanged { if (it.isFocused) onAppFocused(app) }
                            if (continueItem != null) {
                                item(key = "continue") {
                                    ContinueCard(
                                        continueItem,
                                        level2.nowMs,
                                        onClick = { level2.onHistoryClick(continueItem) },
                                        onLongPress = { level2.onHistoryLongPress(continueItem) },
                                        modifier = Modifier.dock(continueItem.app),
                                    )
                                }
                            }
                            items(favorites, key = { it.packageName }) { app ->
                                val m = if (app == favorites.first()) Modifier.focusRequester(favoritesFocus) else Modifier
                                AppTile(
                                    app = app,
                                    tileSize = TileSize.Favorite,
                                    onClick = { onAppClick(app) },
                                    onLongPress = { onAppLongPress(app) },
                                    modifier = m.dock(app),
                                )
                            }
                            item(key = "allApps") {
                                AllAppsTile(onOpenAllApps, Modifier.focusRequester(allAppsFocus).dock(null))
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.home_footer_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = LoungeColors.TertiaryText,
                            modifier = Modifier.padding(horizontal = OverscanHorizontal),
                        )
                    }

                    // ---- Level 2 (a full page below; Down from the dock) ----
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(page)
                            .onFocusChanged { onLevelFocus(2, it.hasFocus) }
                            .padding(vertical = OverscanVertical),
                        verticalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        InputsRow(
                            level2.inputs,
                            onInputClick = level2.onInputClick,
                            onSetUp = level2.onInputsSetUp,
                            onRetry = level2.onInputsRetry,
                            modifier = Modifier.onFocusChanged { if (it.hasFocus) onAppFocused(null) },
                        )
                        RecentlyWatchedRow(
                            level2.history,
                            level2.mediaAccessGranted,
                            level2.nowMs,
                            onClick = level2.onHistoryClick,
                            onLongPress = level2.onHistoryLongPress,
                            onOpenMediaAccess = level2.onOpenMediaAccess,
                        )
                    }
                }
            }
            if (allAppsOpen) {
                AllAppsDrawer(
                    apps = remember(state.home) { drawerApps(state.home) },
                    image = background.image,
                    dim = background.dim,
                    onAppClick = onAppClick,
                    onAppLongPress = onAppLongPress,
                    onClose = onCloseAllApps,
                )
            }
        }
    }
}
