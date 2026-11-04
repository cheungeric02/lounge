package ca.cheungeric.lounge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeTheme

private fun fake(pkg: String, label: String) = AppEntry(pkg, label, banner = null, icon = null, launchIntent = null)

private val previewFavorites = listOf(
    fake("com.stremio.one", "Stremio"),
    fake("com.google.android.youtube.tv", "YouTube"),
    fake("com.netflix.ninja", "Netflix"),
    fake("com.disney.disneyplus", "Disney+"),
    fake("com.amazon.amazonvideo.livingroom", "Prime Video"),
    fake("com.crunchyroll.crunchyroid", "Crunchyroll"),
)

private val previewOthers = listOf(
    fake("a.chrome", "Chrome"),
    fake("a.downloader", "Downloader"),
    fake("a.music", "Music"),
    fake("a.plex", "Plex"),
    fake("a.settings", "Settings"),
    fake("a.spotify", "Spotify"),
    fake("a.twitch", "Twitch"),
)

private val previewWeather = Weather(temperatureC = 4.4, weatherCode = 2, isDay = true, highC = 7.2, lowC = 0.6)

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun HomeScreenPreview() {
    LoungeTheme {
        HomeScreen(
            state = HomeUiState(HomeState(previewFavorites, previewOthers), previewWeather),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
        )
    }
}

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun HomeScreenContinueWatchingPreview() {
    LoungeTheme {
        HomeScreen(
            state = HomeUiState(HomeState(previewFavorites, previewOthers), previewWeather, showContinueWatching = true),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
        )
    }
}

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun HomeScreenNoFavoritesNoWeatherPreview() {
    LoungeTheme {
        HomeScreen(
            state = HomeUiState(HomeState(emptyList(), previewOthers), weather = null),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
        )
    }
}

@Preview(device = Devices.TV_1080p, showBackground = true)
@Composable
private fun HomeScreenNoAppsPreview() {
    LoungeTheme {
        HomeScreen(
            state = HomeUiState(HomeState(), previewWeather),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 200)
@Composable
private fun AppTileStatesPreview() {
    LoungeTheme {
        Row(
            Modifier.background(LoungeColors.Background).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AppTile(previewFavorites[0], TileSize.Favorite, onClick = {}, onLongPress = {})
            AppTile(previewFavorites[1], TileSize.Favorite, onClick = {}, onLongPress = {}, forceFocused = true)
            AppTile(previewOthers[0], TileSize.AllApps, onClick = {}, onLongPress = {}, forceFocused = true)
        }
    }
}
