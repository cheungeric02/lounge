package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.LayoutPrefsState
import ca.cheungeric.lounge.data.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {

    private class FakeStore(initial: LayoutPrefsState) : LayoutStore {
        val flow = MutableStateFlow(initial)
        var updates = 0
        override val state: Flow<LayoutPrefsState> get() = flow
        override suspend fun update(transform: (LayoutPrefsState) -> LayoutPrefsState) {
            updates++
            flow.value = transform(flow.value)
        }
    }

    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, null, null, null)

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val plex = app("com.plexapp.android", "Plex")
    private val kodi = app("org.xbmc.kodi", "Kodi")
    private val apple = app("com.apple.atve", "Apple TV")
    private val all = listOf(netflix, youtube, plex, kodi, apple)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val weather = MutableStateFlow<Weather?>(null)

    @After fun tearDown() = scope.cancel()

    private fun pkgs(l: List<AppEntry>) = l.map { it.packageName }

    private fun HomeLogic.await(pred: (HomeUiState) -> Boolean): HomeUiState =
        runBlocking { withTimeout(5_000) { uiState.first(pred) } }

    private fun initializedStore() = FakeStore(LayoutPrefsState(initialized = true))

    @Test fun initDefaults_runsOnceAfterAppsArrive() = runBlocking {
        val store = FakeStore(LayoutPrefsState())
        val logic = HomeLogic(scope, apps, store, weather)
        Thread.sleep(100)
        assertEquals("no seeding while the app list is empty", 0, store.updates)

        apps.value = all
        val s = logic.await { it.home.favorites.isNotEmpty() }
        assertEquals(listOf(youtube, netflix).map { it.packageName }, pkgs(s.home.favorites))
        assertTrue(store.flow.value.initialized)

        apps.value = all.dropLast(1) // later app-list changes must not re-seed
        apps.value = all
        Thread.sleep(100)
        assertEquals(1, store.updates)
    }

    @Test fun initDefaults_skippedWhenAlreadyInitialized() = runBlocking {
        val store = initializedStore()
        apps.value = all
        val logic = HomeLogic(scope, apps, store, weather)
        logic.await { it.home.others.size == all.size }
        Thread.sleep(100)
        assertEquals(0, store.updates)
    }

    @Test fun toggleFavorite_addsThenRemoves() = runBlocking {
        apps.value = all
        val logic = HomeLogic(scope, apps, initializedStore(), weather)
        logic.await { it.home.others.size == all.size }

        logic.toggleFavorite(plex.packageName).join()
        var s = logic.await { it.home.favorites.isNotEmpty() }
        assertEquals(listOf(plex.packageName), pkgs(s.home.favorites))
        assertTrue(plex !in s.home.others)

        logic.toggleFavorite(plex.packageName).join()
        s = logic.await { it.home.favorites.isEmpty() }
        assertTrue(plex in s.home.others)
    }

    @Test fun hideAndUnhide() = runBlocking {
        apps.value = all
        val logic = HomeLogic(scope, apps, initializedStore(), weather)
        logic.await { it.home.others.size == all.size }

        logic.hide(kodi.packageName).join()
        val s = logic.await { kodi !in it.home.others }
        assertEquals(all.size - 1, s.home.others.size)
        val hidden = withTimeout(5_000) { logic.hiddenApps.first { it.isNotEmpty() } }
        assertEquals(listOf(kodi), hidden)

        logic.unhide(kodi.packageName).join()
        logic.await { kodi in it.home.others }
        assertEquals(emptyList<AppEntry>(), withTimeout(5_000) { logic.hiddenApps.first { it.isEmpty() } })
    }

    @Test fun moveInAllAppsAndFavorites() = runBlocking {
        apps.value = all
        val store = FakeStore(
            LayoutPrefsState(favorites = listOf(netflix.packageName, youtube.packageName), initialized = true),
        )
        val logic = HomeLogic(scope, apps, store, weather)
        // Others alphabetical: Apple TV, Kodi, Plex.
        logic.await { pkgs(it.home.others) == pkgs(listOf(apple, kodi, plex)) }

        logic.moveRight(apple.packageName).join()
        logic.await { pkgs(it.home.others) == pkgs(listOf(kodi, apple, plex)) }

        logic.moveLeft(plex.packageName).join()
        logic.await { pkgs(it.home.others) == pkgs(listOf(kodi, plex, apple)) }

        logic.moveLeft(netflix.packageName).join() // already first: no-op
        logic.moveRight(netflix.packageName).join()
        val s = logic.await { pkgs(it.home.favorites) == pkgs(listOf(youtube, netflix)) }
        assertEquals(pkgs(listOf(kodi, plex, apple)), pkgs(s.home.others))
    }
}
