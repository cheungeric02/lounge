package ca.cheungeric.lounge.data

data class HomeState(
    val favorites: List<AppEntry> = emptyList(),
    val others: List<AppEntry> = emptyList(),
)

/** Pure layout logic: (installed, prefs) -> HomeState. TODO(T2): first-run defaults, custom order, moves. */
object HomeLayout {
    fun build(installed: List<AppEntry>, prefs: LayoutPrefsState): HomeState {
        val favSet = prefs.favorites.toSet()
        val byPkg = installed.associateBy { it.packageName }
        val favorites = prefs.favorites.mapNotNull { byPkg[it] }
        val others = installed
            .filter { it.packageName !in favSet && it.packageName !in prefs.hidden }
            .sortedBy { it.label.lowercase() }
        return HomeState(favorites, others)
    }
}
