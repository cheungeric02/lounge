package ca.cheungeric.lounge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ca.cheungeric.lounge.AppContainer
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HomeLayout
import ca.cheungeric.lounge.data.HomeState
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.LayoutPrefsState
import ca.cheungeric.lounge.data.Weather
import ca.cheungeric.lounge.data.HistoryEntry
import ca.cheungeric.lounge.remote.BraviaControl
import ca.cheungeric.lounge.remote.BraviaRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The slice of [LayoutPrefs] the Home logic needs; faked in unit tests. */
interface LayoutStore {
    val state: Flow<LayoutPrefsState>
    suspend fun update(transform: (LayoutPrefsState) -> LayoutPrefsState)
}

private class LayoutPrefsStore(private val prefs: LayoutPrefs) : LayoutStore {
    override val state: Flow<LayoutPrefsState> get() = prefs.state
    override suspend fun update(transform: (LayoutPrefsState) -> LayoutPrefsState) = prefs.update(transform)
}

/**
 * Framework-free Home logic: combines apps + layout prefs + weather into [uiState], seeds first-run
 * defaults once (after a non-empty app list arrives), and applies layout edits through [store].
 */
class HomeLogic(
    private val scope: CoroutineScope,
    private val apps: StateFlow<List<AppEntry>>,
    private val store: LayoutStore,
    weather: Flow<Weather?>,
) {
    private val prefs: StateFlow<LayoutPrefsState?> = store.state.stateIn(scope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<HomeUiState> =
        combine(apps, prefs.filterNotNull(), weather) { a, p, w ->
            HomeUiState(home = HomeLayout.build(a, p), weather = w)
        }.stateIn(scope, SharingStarted.Eagerly, HomeUiState(HomeState(), null))

    /** Installed apps that are hidden, alphabetical (Settings → Hidden apps). */
    val hiddenApps: StateFlow<List<AppEntry>> =
        combine(apps, prefs.filterNotNull()) { a, p ->
            a.filter { it.packageName in p.hidden }.sortedBy { it.label.lowercase() }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            // Wait for the first stored prefs plus a non-empty app list (or prefs already initialized).
            val p = combine(prefs.filterNotNull(), apps) { p, a -> p to a }
                .first { (p, a) -> p.initialized || a.isNotEmpty() }
                .first
            if (!p.initialized) {
                store.update { HomeLayout.initDefaults(it, installedPkgs()) }
            }
        }
    }

    fun toggleFavorite(pkg: String): Job = edit { HomeLayout.toggleFavorite(it, pkg) }
    fun hide(pkg: String): Job = edit { HomeLayout.hide(it, pkg) }
    fun unhide(pkg: String): Job = edit { HomeLayout.unhide(it, pkg) }
    fun moveLeft(pkg: String): Job = edit { HomeLayout.moveLeft(it, pkg, visibleOthers(it), installedPkgs()) }
    fun moveRight(pkg: String): Job = edit { HomeLayout.moveRight(it, pkg, visibleOthers(it), installedPkgs()) }

    private fun installedPkgs(): List<String> = apps.value.map { it.packageName }

    /** The all-apps row as currently built from the freshest prefs (not a possibly stale UI snapshot). */
    private fun visibleOthers(p: LayoutPrefsState): List<String> =
        HomeLayout.build(apps.value, p).others.map { it.packageName }

    private fun edit(transform: (LayoutPrefsState) -> LayoutPrefsState): Job =
        scope.launch { runCatching { store.update(transform) } }
}

/** Android wrapper: [HomeLogic] in viewModelScope, wired to the [AppContainer], plus the v6 extras. */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val logic = HomeLogic(
        scope = viewModelScope,
        apps = container.appsRepository.apps,
        store = LayoutPrefsStore(container.layoutPrefs),
        weather = container.weatherRepository.weather,
    )

    val uiState: StateFlow<HomeUiState> get() = logic.uiState
    val hiddenApps: StateFlow<List<AppEntry>> get() = logic.hiddenApps

    fun toggleFavorite(pkg: String) { logic.toggleFavorite(pkg) }
    fun hide(pkg: String) { logic.hide(pkg) }
    fun unhide(pkg: String) { logic.unhide(pkg) }
    fun moveLeft(pkg: String) { logic.moveLeft(pkg) }
    fun moveRight(pkg: String) { logic.moveRight(pkg) }

    // ---- v6: HDMI inputs (level 2) ----

    private val _inputs = MutableStateFlow<InputsUiState>(InputsUiState.Loading)
    val inputs: StateFlow<InputsUiState> = _inputs.asStateFlow()
    private var inputsJob: Job? = null
    private var pollJob: Job? = null
    private var level2Seen = false

    /** Re-reads the TV's inputs. Keeps the current tiles on screen while refreshing. */
    fun refreshInputs() {
        if (inputsJob?.isActive == true) return
        inputsJob = viewModelScope.launch {
            val r = runCatching { container.braviaControl.inputs() }
                .getOrElse { BraviaControl.InputsResult.Error(it.javaClass.simpleName) }
            _inputs.value = InputsUiState.of(r)
        }
    }

    private var level2Focused = false
    private var resumed = true

    /** Level 2 focus: refresh on first focus, then every [INPUTS_POLL_MS] while it stays focused. */
    fun onLevel2Focused(focused: Boolean) {
        level2Focused = focused
        if (focused && !level2Seen) {
            level2Seen = true
            refreshInputs()
        }
        updatePolling()
    }

    /** Home resumed / paused: refreshes on resume; polling only runs while resumed. */
    fun onHomeResumed(isResumed: Boolean) {
        resumed = isResumed
        if (isResumed) refreshInputs()
        updatePolling()
    }

    private fun updatePolling() {
        pollJob?.cancel()
        pollJob = null
        if (!level2Focused || !resumed) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(INPUTS_POLL_MS)
                refreshInputs()
            }
        }
    }

    /** Switches the TV; [onFailed] gets the input's display name on failure. */
    fun selectInput(input: BraviaRpc.HdmiInput, onFailed: (String) -> Unit) {
        viewModelScope.launch {
            val r = runCatching { container.braviaControl.setInputUri(input.uri) }.getOrNull()
            if (r is BraviaControl.Result.Ok) {
                val cur = _inputs.value
                if (cur is InputsUiState.Ready) _inputs.value = cur.copy(activeUri = input.uri)
            } else {
                onFailed(input.displayName)
                if (r is BraviaControl.Result.WrongKey) _inputs.value = InputsUiState.WrongKey
            }
        }
    }

    // ---- v6: media history ----

    /** History cards with saved art path + installed app (for the icon fallback / launch). */
    val history: StateFlow<List<HomeHistoryItem>> =
        combine(container.mediaHistory.entries, container.appsRepository.apps) { entries, apps ->
            val byPkg = apps.associateBy { it.packageName }
            entries.map { e ->
                HomeHistoryItem(e, container.mediaHistory.artFileOf(e)?.absolutePath, byPkg[e.packageName])
            }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun removeHistory(e: HistoryEntry) {
        viewModelScope.launch { runCatching { container.mediaHistory.remove(e) } }
    }

    // ---- v6: tint from the focused app ----

    private val tintCache = AppTintCache()
    private val _focusTint = MutableStateFlow<Int?>(null)
    /** ARGB of the focused app's dominant colour; null = fall back to the theme colour. */
    val focusTint: StateFlow<Int?> = _focusTint.asStateFlow()
    private var tintJob: Job? = null

    fun onAppFocused(app: AppEntry?) {
        tintJob?.cancel()
        if (app == null) {
            _focusTint.value = null
            return
        }
        tintJob = viewModelScope.launch {
            _focusTint.value = runCatching { tintCache.colorFor(app) }.getOrNull()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(container) as T
    }

    private companion object {
        const val INPUTS_POLL_MS = 30_000L
    }
}
