package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.ui.theme.LoungeColors

/**
 * Stateless Home screen per SPEC "Home screen". [topContent] renders between the header and the
 * rows (T4: update banner / onboarding tip). Initial focus goes to the first favorite, else the first
 * all-apps tile; D-pad Up from the first row reaches the settings button.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAppClick: (AppEntry) -> Unit,
    onAppLongPress: (AppEntry) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topContent: (@Composable () -> Unit)? = null,
    nowPlaying: HomeNowPlaying? = null,
) {
    val favorites = state.home.favorites
    val others = state.home.others
    val settingsFocus = remember { FocusRequester() }
    val favoritesFocus = remember { FocusRequester() }
    val othersFocus = remember { FocusRequester() }

    // Initial focus: once, on the first tile that exists. Settings is focused while there are no
    // apps (e.g. before the first load) but that does not count as "done".
    var initialFocusDone by remember { mutableStateOf(false) }
    val initialTarget = when {
        favorites.isNotEmpty() -> InitialFocus.Favorites
        others.isNotEmpty() -> InitialFocus.Others
        else -> InitialFocus.Settings
    }
    LaunchedEffect(initialTarget) {
        if (initialFocusDone) return@LaunchedEffect
        withFrameNanos { }
        val requester = when (initialTarget) {
            InitialFocus.Favorites -> favoritesFocus
            InitialFocus.Others -> othersFocus
            InitialFocus.Settings -> settingsFocus
        }
        val ok = runCatching { requester.requestFocus() }.isSuccess
        if (ok && initialTarget != InitialFocus.Settings) initialFocusDone = true
    }

    // Up from the first focusable row goes straight to settings unless topContent sits in between.
    val upToSettings = if (topContent == null) settingsFocus else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoungeColors.Background),
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = OverscanVertical)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = OverscanHorizontal),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClockHeader()
                Spacer(Modifier.weight(1f))
                if (nowPlaying != null) {
                    NowPlayingPill(nowPlaying, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(16.dp))
                }
                WeatherCard(state.weather)
                Spacer(Modifier.width(16.dp))
                SettingsButton(onClick = onSettingsClick, modifier = Modifier.focusRequester(settingsFocus))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (topContent != null) {
                    Box(Modifier.padding(horizontal = OverscanHorizontal)) { topContent() }
                }
                if (state.showContinueWatching) ContinueWatchingPlaceholder()

                if (favorites.isEmpty() && others.isEmpty()) {
                    Text(
                        stringResource(R.string.home_no_apps),
                        style = MaterialTheme.typography.titleMedium,
                        color = LoungeColors.TertiaryText,
                        modifier = Modifier.padding(horizontal = OverscanHorizontal),
                    )
                } else {
                    AppRow(
                        title = stringResource(R.string.home_favorites),
                        apps = favorites,
                        tileSize = TileSize.Favorite,
                        onAppClick = onAppClick,
                        onAppLongPress = onAppLongPress,
                        firstItemFocusRequester = favoritesFocus,
                        upTarget = upToSettings,
                        emptyHint = stringResource(R.string.home_no_favorites_hint),
                    )
                    if (others.isNotEmpty()) {
                        AppRow(
                            title = stringResource(R.string.home_all_apps),
                            apps = others,
                            tileSize = TileSize.AllApps,
                            onAppClick = onAppClick,
                            onAppLongPress = onAppLongPress,
                            firstItemFocusRequester = othersFocus,
                            // With no favorites, this is the first focusable row.
                            upTarget = if (favorites.isEmpty()) upToSettings else null,
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.home_footer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LoungeColors.TertiaryText,
                modifier = Modifier.padding(horizontal = OverscanHorizontal),
            )
        }
    }
}

private enum class InitialFocus { Favorites, Others, Settings }
