package ca.cheungeric.lounge.data

data class HomeState(
    val favorites: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
)

/** Default favorites on first run (SPEC "Home screen"), in display order; only installed ones are used. */
val DEFAULT_FAVORITES: List<String> = listOf(
    "com.stremio.one",                    // Stremio
    "com.google.android.youtube.tv",      // YouTube
    "com.netflix.ninja",                  // Netflix
    "com.disney.disneyplus",              // Disney+
    "com.amazon.amazonvideo.livingroom",  // Prime Video
    "com.crunchyroll.crunchyroid",        // Crunchyroll
)

/**
 * Pure layout logic: (installed, prefs) -> HomeState, plus pure prefs transforms.
 * No Android framework calls, so it runs on the JVM in unit tests.
 */
object HomeLayout {

    fun build(installed: List<AppEntry>, prefs: LayoutPrefsState): HomeState {
        val byPkg = LinkedHashMap<String, AppEntry>()
        for (app in installed) byPkg.putIfAbsent(app.packageName, app)

        // Favorites: stored order; uninstalled skipped (kept in prefs); hidden never shown.
        val favorites = prefs.favorites
            .distinct()
            .filter { it !in prefs.hidden }
            .mapNotNull { byPkg[it] }

        val favSet = prefs.favorites.toSet()
        val candidates = byPkg.values.filter { it.packageName !in favSet && it.packageName !in prefs.hidden }
        val candidateByPkg = candidates.associateBy { it.packageName }

        // All apps: custom order first, then the rest alphabetically (case-insensitive).
        val ordered = prefs.allAppsOrder.distinct().mapNotNull { candidateByPkg[it] }
        val orderedSet = ordered.mapTo(HashSet()) { it.packageName }
        val rest = candidates
            .filter { it.packageName !in orderedSet }
            .sortedWith(compareBy<AppEntry>({ it.label.lowercase() }, { it.packageName }))
        return HomeState(favorites, ordered + rest)
    }

    /** First run: seed favorites with the installed DEFAULT_FAVORITES (SPEC order). No-op once initialized. */
    fun initDefaults(prefs: LayoutPrefsState, installedPkgs: Collection<String>): LayoutPrefsState {
        if (prefs.initialized) return prefs
        val installed = installedPkgs.toSet()
        return prefs.copy(
            favorites = DEFAULT_FAVORITES.filter { it in installed },
            initialized = true,
        )
    }

    /** Add to the end of favorites (also un-hiding it), or remove it if already a favorite. */
    fun toggleFavorite(prefs: LayoutPrefsState, pkg: String): LayoutPrefsState =
        if (pkg in prefs.favorites) {
            prefs.copy(favorites = prefs.favorites.filter { it != pkg })
        } else {
            prefs.copy(favorites = prefs.favorites + pkg, hidden = prefs.hidden - pkg)
        }

    /** Hide from both rows. Favorite status is kept in prefs (it returns as a favorite when unhidden). */
    fun hide(prefs: LayoutPrefsState, pkg: String): LayoutPrefsState =
        prefs.copy(hidden = prefs.hidden + pkg)

    fun unhide(prefs: LayoutPrefsState, pkg: String): LayoutPrefsState =
        prefs.copy(hidden = prefs.hidden - pkg)

    /**
     * Move [pkg] one slot left within whichever row it is in. No-op at the edge or if not visible.
     * [installedVisibleOthersOrder] is the current visible all-apps row (package names, as built).
     * [installedPkgs], if given, limits favorites neighbours to installed apps (uninstalled favorites
     * are invisible, so swapping with one would look like a no-op).
     */
    fun moveLeft(
        prefs: LayoutPrefsState,
        pkg: String,
        installedVisibleOthersOrder: List<String>,
        installedPkgs: Collection<String>? = null,
    ): LayoutPrefsState = move(prefs, pkg, -1, installedVisibleOthersOrder, installedPkgs)

    fun moveRight(
        prefs: LayoutPrefsState,
        pkg: String,
        installedVisibleOthersOrder: List<String>,
        installedPkgs: Collection<String>? = null,
    ): LayoutPrefsState = move(prefs, pkg, +1, installedVisibleOthersOrder, installedPkgs)

    /**
     * Move favorite [pkg] to [index] in the visible favorites row (as [build] shows it: not hidden,
     * and installed when [installedPkgs] is given). [index] is clamped to the row. Invisible stored
     * favorites (hidden/uninstalled) keep their slots. No-op (same instance) if [pkg] is not a
     * visible favorite or the order would not change.
     */
    fun moveFavoriteTo(
        prefs: LayoutPrefsState,
        pkg: String,
        index: Int,
        installedPkgs: Collection<String>? = null,
    ): LayoutPrefsState {
        if (pkg !in prefs.favorites || pkg in prefs.hidden) return prefs
        val installed = installedPkgs?.toSet()
        val favs = prefs.favorites.distinct()
        fun isVisible(p: String) = p !in prefs.hidden && (installed == null || p in installed)
        val visible = favs.filter(::isVisible)
        val from = visible.indexOf(pkg)
        if (from < 0) return prefs
        val to = index.coerceIn(0, visible.lastIndex)
        if (to == from) return prefs
        val reordered = visible.toMutableList().apply {
            removeAt(from)
            add(to, pkg)
        }
        val it = reordered.iterator()
        val result = favs.map { p -> if (isVisible(p)) it.next() else p }
        return if (result == prefs.favorites) prefs else prefs.copy(favorites = result)
    }

    private fun move(
        prefs: LayoutPrefsState,
        pkg: String,
        delta: Int,
        others: List<String>,
        installedPkgs: Collection<String>?,
    ): LayoutPrefsState {
        if (pkg in prefs.hidden) return prefs
        if (pkg in prefs.favorites) {
            val installed = installedPkgs?.toSet()
            val favs = prefs.favorites.distinct()
            val visible = favs.filter { it !in prefs.hidden && (installed == null || it in installed) }
            val i = visible.indexOf(pkg)
            val j = i + delta
            if (i < 0 || j !in visible.indices) return prefs
            val neighbour = visible[j]
            val swapped = favs.map {
                when (it) {
                    pkg -> neighbour
                    neighbour -> pkg
                    else -> it
                }
            }
            return prefs.copy(favorites = swapped)
        }
        val visible = others.distinct()
        val i = visible.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in visible.indices) return prefs
        val materialized = visible.toMutableList()
        materialized[i] = visible[j]
        materialized[j] = pkg
        // Keep stored order entries that aren't currently visible (hidden/favorited/uninstalled) after.
        val visibleSet = visible.toSet()
        val tail = prefs.allAppsOrder.filter { it !in visibleSet }.distinct()
        return prefs.copy(allAppsOrder = materialized + tail)
    }
}
