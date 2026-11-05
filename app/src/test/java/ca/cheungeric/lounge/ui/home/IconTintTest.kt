package ca.cheungeric.lounge.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconTintTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun fill(n: Int, c: Int) = IntArray(n) { c }

    @Test fun solidColour() {
        assertEquals(argb(255, 220, 20, 30), dominantColor(fill(576, argb(255, 220, 20, 30))))
    }

    @Test fun majorityBucketWins_andIsAveraged() {
        val px = fill(10, argb(255, 0, 0, 200)) + fill(10, argb(255, 0, 0, 100)) + fill(5, argb(255, 230, 0, 0))
        assertEquals(argb(255, 0, 0, 150), dominantColor(px))
    }

    @Test fun ignoresTransparentGreyDarkAndLight() {
        val px = fill(100, argb(40, 255, 0, 0)) + // translucent red
            fill(100, argb(255, 128, 128, 128)) + // grey
            fill(100, argb(255, 10, 0, 5)) + // near black
            fill(100, argb(255, 250, 245, 248)) + // near white
            fill(3, argb(255, 30, 160, 60)) // a little green
        assertEquals(argb(255, 30, 160, 60), dominantColor(px))
    }

    @Test fun nothingColourful_isNull() {
        assertNull(dominantColor(IntArray(0)))
        assertNull(dominantColor(fill(64, 0)))
        assertNull(dominantColor(fill(64, argb(255, 255, 255, 255))))
        assertNull(dominantColor(fill(64, argb(255, 90, 92, 95))))
    }

    @Test fun nearbyRedHues_largerGroupWins() {
        // Hue ~354° and ~6° fall in different 30° buckets; the larger group wins.
        val px = fill(6, argb(255, 220, 20, 40)) + fill(4, argb(255, 220, 40, 20))
        assertEquals(argb(255, 220, 20, 40), dominantColor(px))
    }
}
