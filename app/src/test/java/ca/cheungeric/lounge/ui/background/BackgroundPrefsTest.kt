package ca.cheungeric.lounge.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundPrefsTest {

    @Test fun defaults_whenMissing() {
        val s = BackgroundPrefs.fromMap(emptyMap<String, Any>())
        assertEquals(BackgroundSettings(BgImage.Cliffs, 50, AmbientStyle.Webb), s)
    }

    @Test fun parsesKnownValues() {
        val s = BackgroundPrefs.fromMap(
            mapOf(
                BackgroundPrefs.KEY_IMAGE to "deep",
                BackgroundPrefs.KEY_DIM to 70,
                BackgroundPrefs.KEY_AMBIENT_STYLE to "black",
            ),
        )
        assertEquals(BackgroundSettings(BgImage.Deep, 70, AmbientStyle.Black), s)
        assertEquals(BgImage.Pillars, BackgroundPrefs.imageOf("pillars"))
        assertEquals(BgImage.Black, BackgroundPrefs.imageOf("black"))
        assertEquals(30, BackgroundPrefs.dimOf("30"))
        assertEquals(30, BackgroundPrefs.dimOf(30L))
    }

    @Test fun unknownValues_fallBack() {
        assertEquals(BgImage.Cliffs, BackgroundPrefs.imageOf("CLIFFS"))
        assertEquals(BgImage.Cliffs, BackgroundPrefs.imageOf(42))
        assertEquals(BgImage.Cliffs, BackgroundPrefs.imageOf(null))
        assertEquals(50, BackgroundPrefs.dimOf(40))
        assertEquals(50, BackgroundPrefs.dimOf("strong"))
        assertEquals(50, BackgroundPrefs.dimOf(null))
        assertEquals(AmbientStyle.Webb, BackgroundPrefs.ambientStyleOf("neon"))
        assertEquals(AmbientStyle.Webb, BackgroundPrefs.ambientStyleOf(true))
    }

    @Test fun ambientPhoto() {
        assertEquals(BgImage.Pillars, BackgroundSettings(BgImage.Pillars).ambientPhoto)
        assertNull(BackgroundSettings(BgImage.Black).ambientPhoto)
        assertNull(BackgroundSettings(BgImage.Deep, ambientStyle = AmbientStyle.Black).ambientPhoto)
    }
}
