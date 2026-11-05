package ca.cheungeric.lounge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.home.HomeButtonService
import ca.cheungeric.lounge.home.HomeServiceStatus
import ca.cheungeric.lounge.ui.edit.TileAction
import ca.cheungeric.lounge.ui.edit.TileActionSheet
import ca.cheungeric.lounge.ui.home.HomeButtonTip
import ca.cheungeric.lounge.ui.home.HomeScreen
import ca.cheungeric.lounge.ui.home.HomeViewModel
import ca.cheungeric.lounge.ui.home.UpdateBanner
import ca.cheungeric.lounge.ui.patchnotes.APP_VERSION
import ca.cheungeric.lounge.ui.patchnotes.PatchNote
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewDialog
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore
import ca.cheungeric.lounge.ui.patchnotes.checkWhatsNew
import ca.cheungeric.lounge.ui.settings.SettingsScreen
import ca.cheungeric.lounge.ui.settings.canInstall
import ca.cheungeric.lounge.ui.settings.updateStatusText
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes
import ca.cheungeric.lounge.ui.theme.LoungeTheme
import ca.cheungeric.lounge.update.UpdateState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { Home, Settings }

private data class Snack(val id: Long, val text: String)

/** HOME + LEANBACK_LAUNCHER activity: hosts Home / Settings, boot checks, launching. */
class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(Screen.Home)
    private var sheetApp by mutableStateOf<AppEntry?>(null)
    private var whatsNew by mutableStateOf<List<PatchNote>>(emptyList())
    private var snack by mutableStateOf<Snack?>(null)
    private var homeServiceEnabled by mutableStateOf(true)
    private var homeTipDismissed by mutableStateOf(true)
    /** versionCode the user chose "Later" for this session (UpdateManager.dismiss() keeps Available). */
    private var updateLaterFor by mutableStateOf<Int?>(null)

    private val container: AppContainer get() = (application as LoungeApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before anything writes "lounge_prefs".
        val fresh = WhatsNewStore(this).captureFreshInstall()
        super.onCreate(savedInstanceState)

        val vm = ViewModelProvider(this, HomeViewModel.Factory(container))[HomeViewModel::class.java]
        val updates = container.updateManager
        updates.start()
        homeTipDismissed = readTipDismissed()
        homeServiceEnabled = HomeServiceStatus.isEnabled(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.weatherRepository.startAutoRefresh(this)
                awaitCancellation()
            }
        }

        setContent {
            LoungeTheme {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    whatsNew = checkWhatsNew(WhatsNewStore(this@MainActivity), fresh)
                }
                val homeState by vm.uiState.collectAsStateWithLifecycle()
                val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
                val updateState by updates.state.collectAsStateWithLifecycle()

                Box(Modifier.fillMaxSize().background(LoungeColors.Background)) {
                    when (screen) {
                        Screen.Home -> {
                            BackHandler { /* Lounge is the launcher: Back on Home does nothing. */ }
                            HomeScreen(
                                state = homeState,
                                onAppClick = ::launchApp,
                                onAppLongPress = { sheetApp = it },
                                onSettingsClick = { screen = Screen.Settings },
                                topContent = homeTopContent(updateState),
                            )
                        }
                        Screen.Settings -> {
                            BackHandler { screen = Screen.Home }
                            SettingsScreen(
                                appVersion = APP_VERSION,
                                homeServiceEnabled = homeServiceEnabled,
                                updateState = updateState,
                                hiddenApps = hidden,
                                onOpenAccessibility = { HomeServiceStatus.openAccessibilitySettings(this@MainActivity) },
                                onCheckForUpdates = updates::checkNow,
                                onInstallUpdate = updates::install,
                                onUnhide = { vm.unhide(it.packageName) },
                            )
                        }
                    }

                    snack?.let { s ->
                        LaunchedEffect(s.id) {
                            delay(3_000)
                            if (snack?.id == s.id) snack = null
                        }
                        Text(
                            s.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = LoungeColors.OnSurface,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp)
                                .background(LoungeColors.SurfaceVariant, LoungeShapes.Pill)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }

                sheetApp?.let { app ->
                    val isFavorite = homeState.home.favorites.any { it.packageName == app.packageName }
                    TileActionSheet(
                        app = app,
                        isFavorite = isFavorite,
                        onAction = { action ->
                            sheetApp = null
                            when (action) {
                                TileAction.MoveLeft -> vm.moveLeft(app.packageName)
                                TileAction.MoveRight -> vm.moveRight(app.packageName)
                                TileAction.ToggleFavorite -> vm.toggleFavorite(app.packageName)
                                TileAction.Hide -> vm.hide(app.packageName)
                                TileAction.AppInfo -> openAppInfo(app.packageName)
                            }
                        },
                        onDismiss = { sheetApp = null },
                    )
                }

                if (whatsNew.isNotEmpty()) {
                    WhatsNewDialog(whatsNew, onDismiss = { whatsNew = emptyList() })
                }
            }
        }
    }

    /** Update banner (wins) or the Home-button tip; null when neither applies. */
    private fun homeTopContent(updateState: UpdateState): (@Composable () -> Unit)? {
        val info = when (updateState) {
            is UpdateState.Available -> updateState.info
            is UpdateState.Downloading -> updateState.info
            is UpdateState.PermissionRequired -> updateState.info
            is UpdateState.Failed -> updateState.info
            else -> null
        }
        // Keep the banner up while installing so the focused button doesn't vanish mid-flow.
        val installing = updateState is UpdateState.Installing
        if ((info != null && info.versionCode != updateLaterFor) || installing) {
            val code = info?.versionCode
            return {
                UpdateBanner(
                    versionCode = code ?: APP_VERSION,
                    status = if (updateState is UpdateState.Available) null else updateStatusText(updateState),
                    showInstall = canInstall(updateState),
                    onInstall = container.updateManager::install,
                    onLater = {
                        container.updateManager.dismiss()
                        updateLaterFor = code
                    },
                )
            }
        }
        if (!homeServiceEnabled && !homeTipDismissed) {
            return {
                HomeButtonTip(
                    onSetUp = { HomeServiceStatus.openAccessibilitySettings(this) },
                    onDismiss = {
                        homeTipDismissed = true
                        writeTipDismissed()
                    },
                )
            }
        }
        return null
    }

    override fun onStart() {
        super.onStart()
        container.remoteBridge.acquire()
    }

    override fun onStop() {
        container.remoteBridge.release()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        HomeButtonService.setLoungeResumed(true)
        homeServiceEnabled = HomeServiceStatus.isEnabled(this)
        container.appsRepository.refresh()
    }

    override fun onPause() {
        HomeButtonService.setLoungeResumed(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val fromHome = intent.getBooleanExtra(HomeButtonService.EXTRA_FROM_HOME_BUTTON, false) ||
            intent.hasCategory(Intent.CATEGORY_HOME)
        if (fromHome) {
            screen = Screen.Home
            sheetApp = null
            whatsNew = emptyList()
        }
    }

    private fun launchApp(app: AppEntry) {
        val launch = app.launchIntent
        val ok = launch != null && try {
            startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
        if (!ok) showSnack(getString(R.string.cant_open_app, app.label))
    }

    private fun openAppInfo(pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        HomeButtonService.allowStockLauncherUntil(SystemClock.uptimeMillis() + 3_000)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            showSnack(getString(R.string.cant_open_app_info))
        }
    }

    private fun showSnack(text: String) {
        snack = Snack(SystemClock.uptimeMillis(), text)
    }

    private fun readTipDismissed(): Boolean = try {
        getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TIP_DISMISSED, false)
    } catch (_: Exception) {
        false
    }

    private fun writeTipDismissed() {
        try {
            getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_TIP_DISMISSED, true).apply()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY_TIP_DISMISSED = "lounge_homeTipDismissed"
    }
}
