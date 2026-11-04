package ca.cheungeric.lounge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, null, null, null)

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val stremio = app("com.stremio.one", "Stremio")
    private val crunchy = app("com.crunchyroll.crunchyroid", "Crunchyroll")
    private val plex = app("com.plexapp.android", "Plex")
    private val kodi = app("org.xbmc.kodi", "kodi")
    private val apple = app("com.apple.atve", "Apple TV")

    private val installed = listOf(netflix, youtube, stremio, crunchy, plex, kodi, apple)

    private fun pkgs(list: List<AppEntry>) = list.map { it.packageName }

    // --- first-run defaults ---

    @Test
    fun initDefaults_onlyInstalled_inSpecOrder() {
        val p = HomeLayout.initDefaults(LayoutPrefsState(), pkgs(installed))
        assertTrue(p.initialized)
        assertEquals(
            listOf("com.stremio.one", "com.google.android.youtube.tv", "com.netflix.ninja", "com.crunchyroll.crunchyroid"),
            p.favorites,
        )
    }

    @Test
    fun initDefaults_noopWhenInitialized() {
        val p = LayoutPrefsState(favorites = emptyList(), initialized = true)
        assertSame(p, HomeLayout.initDefaults(p, pkgs(installed)))
    }

    @Test
    fun defaultFavorites_matchSpec() {
        assertEquals(6, DEFAULT_FAVORITES.size)
        assertEquals("com.stremio.one", DEFAULT_FAVORITES.first())
        assertEquals("com.crunchyroll.crunchyroid", DEFAULT_FAVORITES.last())
    }

    @Test
    fun build_firstRun_othersAlphabeticalCaseInsensitive() {
        val p = HomeLayout.initDefaults(LayoutPrefsState(), pkgs(installed))
        val s = HomeLayout.build(installed, p)
        assertEquals(listOf(stremio, youtube, netflix, crunchy), s.favorites)
        assertEquals(listOf(apple, kodi, plex), s.others)
    }

    // --- uninstalled favorite ---

    @Test
    fun uninstalledFavorite_droppedFromBuild_keptInPrefs_returnsWhenReinstalled() {
        val p = LayoutPrefsState(favorites = listOf("com.stremio.one", "com.netflix.ninja"), initialized = true)
        val without = installed - stremio
        val s = HomeLayout.build(without, p)
        assertEquals(listOf(netflix), s.favorites)
        assertFalse(s.others.contains(stremio))
        assertTrue("com.stremio.one" in p.favorites)
        assertEquals(listOf(stremio, netflix), HomeLayout.build(installed, p).favorites)
    }

    // --- hide / unhide ---

    @Test
    fun hidden_excludedFromBothRows_includingFavorites() {
        var p = LayoutPrefsState(favorites = listOf("com.netflix.ninja", "com.stremio.one"), initialized = true)
        p = HomeLayout.hide(p, "com.netflix.ninja")
        p = HomeLayout.hide(p, "com.plexapp.android")
        val s = HomeLayout.build(installed, p)
        assertEquals(listOf(stremio), s.favorites)
        assertFalse(s.others.contains(netflix))
        assertFalse(s.others.contains(plex))
        assertTrue("com.netflix.ninja" in p.favorites)

        p = HomeLayout.unhide(HomeLayout.unhide(p, "com.netflix.ninja"), "com.plexapp.android")
        val s2 = HomeLayout.build(installed, p)
        assertEquals(listOf(netflix, stremio), s2.favorites)
        assertTrue(s2.others.contains(plex))
    }

    // --- toggleFavorite ---

    @Test
    fun toggleFavorite_addsToEnd_thenRemovesBackToOthers() {
        var p = LayoutPrefsState(favorites = listOf("com.netflix.ninja"), initialized = true)
        p = HomeLayout.toggleFavorite(p, "com.plexapp.android")
        assertEquals(listOf("com.netflix.ninja", "com.plexapp.android"), p.favorites)
        var s = HomeLayout.build(installed, p)
        assertEquals(listOf(netflix, plex), s.favorites)
        assertFalse(s.others.contains(plex))

        p = HomeLayout.toggleFavorite(p, "com.plexapp.android")
        assertEquals(listOf("com.netflix.ninja"), p.favorites)
        s = HomeLayout.build(installed, p)
        assertTrue(s.others.contains(plex))
    }

    // --- moves in favorites ---

    @Test
    fun moveInFavorites_swapsAndStopsAtEdges() {
        val p = LayoutPrefsState(favorites = listOf("a", "b", "c"), initialized = true)
        val apps = listOf(app("a"), app("b"), app("c"), app("z"))
        val others = pkgs(HomeLayout.build(apps, p).others)

        assertSame(p, HomeLayout.moveLeft(p, "a", others))
        assertSame(p, HomeLayout.moveRight(p, "c", others))
        assertEquals(listOf("b", "a", "c"), HomeLayout.moveLeft(p, "b", others).favorites)
        assertEquals(listOf("a", "c", "b"), HomeLayout.moveRight(p, "b", others).favorites)
    }

    @Test
    fun moveInFavorites_skipsInvisibleNeighbours() {
        // "x" is uninstalled, "h" hidden: moving "c" left should swap with "a".
        val p = LayoutPrefsState(favorites = listOf("a", "x", "h", "c"), hidden = setOf("h"), initialized = true)
        val moved = HomeLayout.moveLeft(p, "c", emptyList(), installedPkgs = listOf("a", "h", "c"))
        assertEquals(listOf("c", "x", "h", "a"), moved.favorites)
        assertEquals(listOf(app("c"), app("a")), HomeLayout.build(listOf(app("a"), app("h"), app("c")), moved).favorites)
    }

    // --- moves in all apps ---

    @Test
    fun moveInAllApps_materializesOrder_andStopsAtEdges() {
        val p = LayoutPrefsState(favorites = listOf("com.netflix.ninja"), initialized = true)
        val others = pkgs(HomeLayout.build(installed, p).others)
        // alphabetical: Apple TV, Crunchyroll, kodi, Plex, Stremio, YouTube
        assertEquals(listOf(apple, crunchy, kodi, plex, stremio, youtube).map { it.packageName }, others)

        assertSame(p, HomeLayout.moveLeft(p, apple.packageName, others))
        assertSame(p, HomeLayout.moveRight(p, youtube.packageName, others))

        val moved = HomeLayout.moveRight(p, apple.packageName, others)
        assertEquals(listOf(crunchy, apple, kodi, plex, stremio, youtube).map { it.packageName }, moved.allAppsOrder)
        assertEquals(listOf(crunchy, apple, kodi, plex, stremio, youtube), HomeLayout.build(installed, moved).others)

        val back = HomeLayout.moveLeft(moved, apple.packageName, pkgs(HomeLayout.build(installed, moved).others))
        assertEquals(others, pkgs(HomeLayout.build(installed, back).others))
    }

    @Test
    fun customOrder_stable_newAppAppearsAlphabeticallyAfterOrdered() {
        val p = LayoutPrefsState(allAppsOrder = listOf("org.xbmc.kodi", "com.apple.atve"), initialized = true)
        val base = listOf(apple, kodi, plex)
        assertEquals(listOf(kodi, apple, plex), HomeLayout.build(base, p).others)

        val bravo = app("com.bravo", "Bravo")
        val zulu = app("com.zulu", "zulu")
        val s = HomeLayout.build(listOf(zulu, apple, bravo, kodi, plex), p)
        assertEquals(listOf(kodi, apple, bravo, plex, zulu), s.others)
        // Order is independent of the installed list's order (stable across refreshes).
        assertEquals(s, HomeLayout.build(listOf(plex, kodi, bravo, zulu, apple), p))
    }

    @Test
    fun allAppsMove_keepsInvisibleStoredEntries() {
        val p = LayoutPrefsState(allAppsOrder = listOf("gone", "org.xbmc.kodi"), initialized = true)
        val apps = listOf(apple, kodi)
        val others = pkgs(HomeLayout.build(apps, p).others) // kodi, apple
        val moved = HomeLayout.moveRight(p, "org.xbmc.kodi", others)
        assertEquals(listOf("com.apple.atve", "org.xbmc.kodi", "gone"), moved.allAppsOrder)
    }

    @Test
    fun moveUnknownPackage_isNoop() {
        val p = LayoutPrefsState(initialized = true)
        assertSame(p, HomeLayout.moveLeft(p, "nope", listOf("a", "b")))
    }

    // --- prefs codec ---

    @Test
    fun stringListCodec_roundTripsAndTreatsCorruptAsEmpty() {
        val list = listOf("a", "b.c", "d")
        assertEquals(list, StringListCodec.decode(StringListCodec.encode(list)))
        assertEquals(emptyList<String>(), StringListCodec.decode("{not json"))
        assertEquals(emptyList<String>(), StringListCodec.decode("[1, {}]"))
        assertEquals(emptyList<String>(), StringListCodec.decode(null))
        assertEquals(emptyList<String>(), StringListCodec.decode(""))
    }
}
