package ca.cheungeric.lounge.home

import ca.cheungeric.lounge.home.HomeServiceStatus.SettingsTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAccessStatusTest {
    private val targets = MediaAccessStatus.NOTIFICATION_ACCESS_TARGETS

    @Test
    fun targetOrder_actionThenTvScreensThenSettings() {
        assertEquals(
            listOf(
                SettingsTarget(action = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
                SettingsTarget("com.android.tv.settings", "com.android.tv.settings.device.apps.specialaccess.NotificationAccessActivity"),
                SettingsTarget("com.android.tv.settings", "com.android.tv.settings.device.apps.specialaccess.SpecialAppAccessActivity"),
                SettingsTarget(action = "android.settings.SETTINGS"),
            ),
            targets,
        )
    }

    @Test
    fun skipsUnresolvable_keepsOrder() {
        val got = HomeServiceStatus.startableInOrder(targets) { it.pkg != null }
        assertEquals(listOf(targets[1], targets[2]), got)
    }
}
