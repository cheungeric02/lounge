package ca.cheungeric.lounge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import ca.cheungeric.lounge.home.MediaAccessStatus
import ca.cheungeric.lounge.remote.MediaWatcher
import ca.cheungeric.lounge.remote.NowPlaying
import ca.cheungeric.lounge.ui.ambient.AmbientIdle
import ca.cheungeric.lounge.ui.ambient.AmbientScreen
import ca.cheungeric.lounge.ui.background.BackgroundPrefs
import ca.cheungeric.lounge.ui.background.BackgroundSettings
import ca.cheungeric.lounge.ui.edit.HistoryActionSheet
import ca.cheungeric.lounge.ui.edit.TileAction
import ca.cheungeric.lounge.ui.edit.TileActionSheet
import ca.cheungeric.lounge.ui.home.HomeButtonTip
import ca.cheungeric.lounge.ui.home.HomeHistoryItem
import ca.cheungeric.lounge.ui.home.HomeLevel2
import ca.cheungeric.lounge.ui.home.HomeNowPlaying
import ca.cheungeric.lounge.ui.home.HomeScreen
import ca.cheungeric.lounge.ui.home.HomeViewModel
import ca.cheungeric.lounge.ui.home.UpdateBanner
import ca.cheungeric.lounge.ui.patchnotes.APP_VERSION
import ca.cheungeric.lounge.ui.patchnotes.PatchNote
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewDialog
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore
import ca.cheungeric.lounge.ui.patchnotes.checkWhatsNew
import ca.cheungeric.lounge.ui.settings.BraviaTestState
import ca.cheungeric.lounge.ui.settings.SettingsScreen
import ca.cheungeric.lounge.ui.settings.canInstall
import ca.cheungeric.lounge.ui.settings.updateStatusText
import ca.cheungeric.lounge.ui.theme.LoungeColors
import ca.cheungeric.lounge.ui.theme.LoungeShapes
import ca.cheungeric.lounge.ui.theme.LoungeTheme
import ca.cheungeric.lounge.update.UpdateState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class Screen { Home, Settings }

private data class Snack(val id: Long, val text: String)

/** HOME + LEANBACK_LAUNCHER activity: hosts Home / Settings, boot checks, launching. */
class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(Screen.Home)
    private var sheetApp by mutableStateOf<AppEntry?>(null)
    /** v6: hold-OK sheet for a Continue / Recently watched card. */
    private var sheetHistory by mutableStateOf<HomeHistoryItem?>(null)
    /** v6: All apps drawer over Home. */
    private var allAppsOpen by mutableStateOf(false)
    /** v6: Home / ambient background (Settings -> Background). */
    private var background by mutableStateOf(BackgroundSettings())
    private var whatsNew by mutableStateOf<List<PatchNote>>(emptyList())
    private var snack by mutableStateOf<Snack?>(null)
    private var homeServiceEnabled by mutableStateOf(true)
    private var mediaAccessGranted by mutableStateOf(false)
    private var nowPlaying by mutableStateOf<HomeNowPlaying?>(null)
    private var homeTipDismissed by mutableStateOf(true)
    /** Mirrors RemoteBridge.isEnabled (Settings → Phone remote). */
    private var remoteEnabled by mutableStateOf(false)
    /** v5 Settings → TV power control: saved values + Save/Test feedback. */
    private var braviaIp by mutableStateOf("")
    private var braviaPsk by mutableStateOf("")
    private var braviaBoxPort by mutableIntStateOf(2)
    private var braviaTest by mutableStateOf<BraviaTestState>(BraviaTestState.Idle)
    /** versionCode the user chose "Later" for this session (UpdateManager.dismiss() keeps Available). */
    private var updateLaterFor by mutableStateOf<Int?>(null)
    /** v5 ambient screen: idle minutes (0 = Off), overlay visibility, last key uptime. */
    private var ambientMinutes by mutableIntStateOf(AmbientIdle.DEFAULT_MINUTES)
    private var ambientShown by mutableStateOf(false)
    private var lastInputAt = 0L
    /** Keys whose DOWN dismissed the ambient screen: their repeats + UP are swallowed too. */
    private val swallowKeys = HashSet<Int>()

    private val container: AppContainer get() = (application as LoungeApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before anything writes "lounge_prefs".
        val fresh = WhatsNewStore(this).captureFreshInstall()
        super.onCreate(savedInstanceState)

        val vm = homeVm()
        val updates = container.updateManager
        updates.start()
        val bridge = container.remoteBridge
        remoteEnabled = bridge.isEnabled
        val bravia = container.braviaControl
        braviaIp = bravia.ip
        braviaBoxPort = bravia.boxPort
        braviaPsk = bravia.psk
        homeTipDismissed = readTipDismissed()
        homeServiceEnabled = HomeServiceStatus.isEnabled(this)
        mediaAccessGranted = MediaAccessStatus.isGranted(this)
        ambientMinutes = readAmbientMinutes()
        background = BackgroundPrefs.read(this)
        lifecycleScope.launch { runCatching { container.mediaHistory.cleanOrphans() } }

        // v5: idle → ambient overlay, only while resumed on plain Home (no Settings / sheet / dialog).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                lastInputAt = SystemClock.uptimeMillis()
                while (true) {
                    val plainHome = screen == Screen.Home && sheetApp == null && sheetHistory == null && whatsNew.isEmpty()
                    when (val d = AmbientIdle.decide(ambientMinutes, lastInputAt, SystemClock.uptimeMillis(), plainHome)) {
                        AmbientIdle.Decision.Off, AmbientIdle.Decision.Blocked -> {
                            awaitIdleInputsChange(null)
                            lastInputAt = SystemClock.uptimeMillis()
                        }
                        is AmbientIdle.Decision.Wait -> {
                            if (awaitIdleInputsChange(d.millis)) lastInputAt = SystemClock.uptimeMillis()
                        }
                        AmbientIdle.Decision.Show -> {
                            ambientShown = true
                            snapshotFlow { ambientShown }.first { !it }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.weatherRepository.startAutoRefresh(this)
                awaitCancellation()
            }
        }

        // v3: Home Now Playing pill. Own watcher (works with Phone remote off); retries while no access.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val watcher = MediaWatcher(this@MainActivity) { pkg ->
                    container.appsRepository.apps.value.firstOrNull { it.packageName == pkg }?.label ?: pkg
                }
                while (true) {
                    watcher.reports().collect { r ->
                        val chosen = if (r.access) NowPlaying.choose(r.sessions, android.os.SystemClock.elapsedRealtime()) else null
                        val art = chosen?.let { r.art[it.pkg] }?.let { runCatching { it.asImageBitmap() }.getOrNull() }
                        nowPlaying = HomeNowPlaying.of(chosen, art)
                        // v6: remember what was played for Continue / Recently watched.
                        if (r.access) {
                            r.sessions.filter { it.isPlaying && it.title.isNotBlank() }.forEach { s ->
                                runCatching {
                                    container.mediaHistory.record(s.pkg, s.app, s.title, s.subtitle.ifBlank { null }, r.art[s.pkg])
                                }
                            }
                        }
                    }
                    nowPlaying = null
                    delay(30_000)
                }
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
                val remoteKey by bridge.key.collectAsStateWithLifecycle()
                val remoteStatus by bridge.status.collectAsStateWithLifecycle()
                val inputs by vm.inputs.collectAsStateWithLifecycle()
                val history by vm.history.collectAsStateWithLifecycle()
                val focusTint by vm.focusTint.collectAsStateWithLifecycle()
                // Ticks the "2 h ago" labels once a minute.
                val nowMs by produceState(System.currentTimeMillis()) {
                    while (true) {
                        delay(60_000)
                        value = System.currentTimeMillis()
                    }
                }

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
                                nowPlaying = nowPlaying,
                                background = background,
                                focusTint = focusTint?.let { Color(it) },
                                onAppFocused = vm::onAppFocused,
                                level2 = HomeLevel2(
                                    inputs = inputs,
                                    history = history,
                                    mediaAccessGranted = mediaAccessGranted,
                                    nowMs = nowMs,
                                    onInputClick = { input ->
                                        vm.selectInput(input) { name -> showSnack(getString(R.string.input_switch_failed, name)) }
                                    },
                                    onInputsSetUp = { screen = Screen.Settings },
                                    onInputsRetry = vm::refreshInputs,
                                    onHistoryClick = ::launchHistory,
                                    onHistoryLongPress = { sheetHistory = it },
                                    onOpenMediaAccess = { MediaAccessStatus.openSettings(this@MainActivity) },
                                    onFocused = vm::onLevel2Focused,
                                ),
                                allAppsOpen = allAppsOpen,
                                onOpenAllApps = { allAppsOpen = true },
                                onCloseAllApps = { allAppsOpen = false },
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
                                remoteEnabled = remoteEnabled,
                                remoteKey = remoteKey,
                                remoteStatus = remoteStatus,
                                onRemoteToggle = { on ->
                                    bridge.setEnabled(on)
                                    remoteEnabled = bridge.isEnabled
                                },
                                onRemoteNewKey = bridge::regenerateKey,
                                mediaAccessGranted = mediaAccessGranted,
                                onOpenMediaAccess = { MediaAccessStatus.openSettings(this@MainActivity) },
                                braviaIp = braviaIp,
                                braviaPsk = braviaPsk,
                                braviaTest = braviaTest,
                                braviaBoxPort = braviaBoxPort,
                                onBraviaBoxPort = { p ->
                                    bravia.saveBoxPort(p)
                                    braviaBoxPort = bravia.boxPort
                                },
                                onBraviaSave = { ip, psk ->
                                    bravia.save(ip, psk)
                                    braviaIp = bravia.ip
                                    braviaPsk = bravia.psk
                                    braviaTest = BraviaTestState.Saved
                                },
                                onBraviaTest = { ip, psk ->
                                    bravia.save(ip, psk)
                                    braviaIp = bravia.ip
                                    braviaPsk = bravia.psk
                                    braviaTest = BraviaTestState.Testing
                                    lifecycleScope.launch {
                                        braviaTest = BraviaTestState.Done(bravia.test())
                                    }
                                },
                                ambientMinutes = ambientMinutes,
                                onAmbientMinutes = { m ->
                                    ambientMinutes = AmbientIdle.normalize(m)
                                    writeAmbientMinutes(ambientMinutes)
                                },
                                background = background,
                                onBackgroundChange = { b ->
                                    background = b
                                    BackgroundPrefs.write(this@MainActivity, b)
                                },
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

                    // Drawn over Home without taking focus, so the focused tile is kept for later.
                    if (ambientShown) AmbientScreen(homeState.weather, photo = background.ambientPhoto)
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

                sheetHistory?.let { item ->
                    HistoryActionSheet(
                        title = item.entry.title,
                        onRemove = {
                            sheetHistory = null
                            vm.removeHistory(item.entry)
                        },
                        onDismiss = { sheetHistory = null },
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
        mediaAccessGranted = MediaAccessStatus.isGranted(this)
        container.appsRepository.refresh()
        // v6: Home's HDMI tiles reflect the TV as it is now.
        homeVm().onHomeResumed(true)
    }

    override fun onPause() {
        homeVm().onHomeResumed(false)
        ambientShown = false
        swallowKeys.clear()
        HomeButtonService.setLoungeResumed(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val fromHome = intent.getBooleanExtra(HomeButtonService.EXTRA_FROM_HOME_BUTTON, false) ||
            intent.hasCategory(Intent.CATEGORY_HOME)
        if (fromHome) {
            ambientShown = false
            screen = Screen.Home
            sheetApp = null
            sheetHistory = null
            allAppsOpen = false
            whatsNew = emptyList()
        }
    }

    /**
     * Any key counts as activity; while the ambient screen shows, the key only dismisses it.
     * RestrictedApi is a known false positive: androidx.core.app.ComponentActivity marks its
     * dispatchKeyEvent override @RestrictTo, but this is the public Activity.dispatchKeyEvent.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInputAt = SystemClock.uptimeMillis()
        if (ambientShown) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                ambientShown = false
                swallowKeys.add(event.keyCode)
            }
            return true
        }
        if (event.keyCode in swallowKeys) {
            if (event.action == KeyEvent.ACTION_UP) swallowKeys.remove(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Suspends until the ambient setting, the screen or a sheet/dialog changes (true), or until
     * [timeoutMs] passes (false). null = no timeout.
     */
    private suspend fun awaitIdleInputsChange(timeoutMs: Long?): Boolean =
        withTimeoutOrNull(timeoutMs ?: Long.MAX_VALUE) {
            snapshotFlow { Triple(ambientMinutes, screen, sheetApp != null || sheetHistory != null || whatsNew.isNotEmpty()) }
                .drop(1)
                .first()
            true
        } ?: false

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

    private fun homeVm(): HomeViewModel =
        ViewModelProvider(this, HomeViewModel.Factory(container))[HomeViewModel::class.java]

    /** v6: resume a Continue / Recently watched item by opening its app. */
    private fun launchHistory(item: HomeHistoryItem) {
        val app = item.app ?: container.appsRepository.apps.value.firstOrNull { it.packageName == item.entry.packageName }
        if (app != null) launchApp(app) else showSnack(getString(R.string.cant_open_app, item.entry.appLabel))
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

    private fun readAmbientMinutes(): Int = try {
        AmbientIdle.normalize(
            getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE)
                .getInt(AmbientIdle.PREF_KEY, AmbientIdle.DEFAULT_MINUTES),
        )
    } catch (_: Exception) {
        AmbientIdle.DEFAULT_MINUTES
    }

    private fun writeAmbientMinutes(minutes: Int) {
        try {
            getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE).edit()
                .putInt(AmbientIdle.PREF_KEY, minutes).apply()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY_TIP_DISMISSED = "lounge_homeTipDismissed"
    }
}
