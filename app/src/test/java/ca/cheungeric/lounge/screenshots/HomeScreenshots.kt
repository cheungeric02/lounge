package ca.cheungeric.lounge.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HistoryEntry
import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.remote.BraviaRpc.HdmiInput
import ca.cheungeric.lounge.ui.ambient.AmbientScreen
import ca.cheungeric.lounge.ui.background.BackgroundSettings
import ca.cheungeric.lounge.ui.background.BgImage
import ca.cheungeric.lounge.ui.background.WebbBackdrop
import ca.cheungeric.lounge.ui.home.AllAppsDrawer
import ca.cheungeric.lounge.ui.home.HomeHistoryItem
import ca.cheungeric.lounge.ui.home.HomeLevel2
import ca.cheungeric.lounge.ui.home.HomeScreen
import ca.cheungeric.lounge.ui.home.HomeUiState
import ca.cheungeric.lounge.ui.home.InputsRow
import ca.cheungeric.lounge.ui.home.InputsUiState
import ca.cheungeric.lounge.ui.home.RecentlyWatchedRow
import ca.cheungeric.lounge.ui.home.drawerApps
import ca.cheungeric.lounge.ui.theme.LoungeTheme
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/**
 * JVM screenshots of the v6 screens with sample data (Paparazzi, test-only). Plain `testDebugUnitTest`
 * just renders them (a crash check); `recordPaparazziDebug` writes PNGs to app/src/test/snapshots.
 */
class HomeScreenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920,
            screenHeight = 1080,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE,
            softButtons = false,
        ),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private fun fake(pkg: String, label: String) = AppEntry(pkg, label, banner = null, icon = null, launchIntent = null)

    private val favorites = listOf(
        fake("com.google.android.youtube.tv", "YouTube"),
        fake("com.netflix.ninja", "Netflix"),
        fake("com.disney.disneyplus", "Disney+"),
        fake("com.amazon.amazonvideo.livingroom", "Prime Video"),
        fake("com.crunchyroll.crunchyroid", "Crunchyroll"),
    )
    private val others = listOf(
        fake("a.chrome", "Chrome"), fake("a.downloader", "Downloader"), fake("a.music", "Music"),
        fake("a.plex", "Plex"), fake("a.settings", "Settings"), fake("a.spotify", "Spotify"),
        fake("a.twitch", "Twitch"), fake("a.stremio", "Stremio"), fake("a.files", "Files"),
    )
    private val weather = Weather(temperatureC = 4.4, weatherCode = 2, isDay = true, highC = 7.2, lowC = 0.6)
    private val now = 1_800_000_000_000L
    private val history = listOf(
        HomeHistoryItem(HistoryEntry("com.netflix.ninja", "Netflix", "The Expanse", "S3 · E4", null, now - 2 * 3_600_000L), app = favorites[1]),
        HomeHistoryItem(HistoryEntry("com.google.android.youtube.tv", "YouTube", "Webb's deepest image, explained", null, null, now - 26 * 3_600_000L), app = favorites[0]),
        HomeHistoryItem(HistoryEntry("com.crunchyroll.crunchyroid", "Crunchyroll", "Frieren", "E12", null, now - 3 * 24 * 3_600_000L), app = favorites[4]),
    )
    private val inputs = listOf(
        HdmiInput("extInput:hdmi?port=1", 1, "HDMI 1", "", connected = false),
        HdmiInput("extInput:hdmi?port=2", 2, "HDMI 2", "PlayStation 5", connected = true),
        HdmiInput("extInput:hdmi?port=3", 3, "HDMI 3/ARC", "Google TV", connected = true),
        HdmiInput("extInput:hdmi?port=4", 4, "HDMI 4", "", connected = false),
    )
    private val bg = BackgroundSettings(image = BgImage.Cliffs)

    private fun shot(name: String, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) {
            // Paparazzi 1.3 leaves inspection mode off; on, the Webb photo decodes inline (no async load).
            CompositionLocalProvider(LocalInspectionMode provides true) { LoungeTheme { content() } }
        }

    @Test
    fun home() = shot("1_home") {
        HomeScreen(
            state = HomeUiState(HomeState(favorites, others), weather),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
            background = bg,
            level2 = HomeLevel2(history = history, nowMs = now),
        )
    }

    /** Level 2 as it looks after pressing Down (same rows and backdrop HomeScreen pages to). */
    @Test
    fun level2() = shot("2_inputs_and_history") {
        Box(Modifier.fillMaxSize()) {
            WebbBackdrop(bg.image, bg.dim)
            Column(Modifier.fillMaxSize().padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                InputsRow(InputsUiState.Ready(inputs, activeUri = inputs[1].uri), onInputClick = {}, onSetUp = {}, onRetry = {})
                RecentlyWatchedRow(history, true, now, onClick = {}, onLongPress = {}, onOpenMediaAccess = {})
            }
        }
    }

    @Test
    fun allApps() = shot("3_all_apps") {
        AllAppsDrawer(
            apps = drawerApps(HomeState(favorites, others)),
            image = bg.image,
            dim = bg.dim,
            onAppClick = {}, onAppLongPress = {}, onClose = {},
        )
    }

    @Test
    fun ambient() = shot("4_screensaver") { AmbientScreen(weather, photo = BgImage.Cliffs) }

    @Test
    fun homePillars() = shot("5_home_pillars") {
        HomeScreen(
            state = HomeUiState(HomeState(favorites, others), weather),
            onAppClick = {}, onAppLongPress = {}, onSettingsClick = {},
            background = BackgroundSettings(image = BgImage.Pillars),
            level2 = HomeLevel2(history = history, nowMs = now),
        )
    }
}
