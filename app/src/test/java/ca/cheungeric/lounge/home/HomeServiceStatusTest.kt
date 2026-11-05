package ca.cheungeric.lounge.home

import ca.cheungeric.lounge.home.HomeServiceStatus.SettingsTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeServiceStatusTest {
    private val targets = HomeServiceStatus.ACCESSIBILITY_TARGETS

    @Test
    fun targetOrder_tvComponentsFirstThenActions() {
        assertEquals(
            listOf(
                SettingsTarget("com.android.tv.settings", "com.android.tv.settings.system.AccessibilityActivity"),
                SettingsTarget("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
                SettingsTarget(action = "android.settings.ACCESSIBILITY_SETTINGS"),
                SettingsTarget(action = "android.settings.SETTINGS"),
            ),
            targets,
        )
    }

    @Test
    fun allStartable_keepsOrder() {
        assertEquals(targets, HomeServiceStatus.startableInOrder(targets) { true })
    }

    @Test
    fun skipsUnresolvable_keepsRelativeOrder() {
        val got = HomeServiceStatus.startableInOrder(targets) { it.cls?.contains(".system.") != true }
        assertEquals(listOf(targets[1], targets[2], targets[3]), got)
    }

    @Test
    fun onlyFallbackSettings() {
        val got = HomeServiceStatus.startableInOrder(targets) { it.action == "android.settings.SETTINGS" }
        assertEquals(listOf(targets[3]), got)
    }

    @Test
    fun noneStartable_empty() {
        assertTrue(HomeServiceStatus.startableInOrder(targets) { false }.isEmpty())
    }

    @Test
    fun throwingCheck_treatedAsNotStartable() {
        val got = HomeServiceStatus.startableInOrder(targets) {
            if (it.pkg != null) throw SecurityException("hidden") else true
        }
        assertEquals(listOf(targets[2], targets[3]), got)
    }
}
