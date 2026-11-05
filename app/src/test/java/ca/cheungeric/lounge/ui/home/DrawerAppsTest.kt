package ca.cheungeric.lounge.ui.home

import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HomeState
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerAppsTest {
    private fun app(pkg: String, label: String) = AppEntry(pkg, label, banner = null, icon = null, launchIntent = null)

    @Test fun favouritesAndOthers_sortedCaseInsensitive() {
        val home = HomeState(
            favorites = listOf(app("n", "netflix"), app("y", "YouTube")),
            others = listOf(app("c", "Chrome"), app("a", "apple tv")),
        )
        assertEquals(listOf("apple tv", "Chrome", "netflix", "YouTube"), drawerApps(home).map { it.label })
    }

    @Test fun empty() {
        assertEquals(emptyList<AppEntry>(), drawerApps(HomeState()))
    }
}
