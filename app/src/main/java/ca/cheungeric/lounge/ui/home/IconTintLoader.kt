package ca.cheungeric.lounge.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import ca.cheungeric.lounge.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-package cache of [dominantColor] over the app's banner (else icon) drawn at 24×24. Work runs on
 * Dispatchers.Default against a copy of the drawable (never the instance the UI is drawing).
 */
class AppTintCache {
    private val cache = ConcurrentHashMap<String, Int>()

    /** ARGB colour, or null when the app has no colourful art. */
    suspend fun colorFor(app: AppEntry): Int? {
        cache[app.packageName]?.let { return it.takeIf { c -> c != NONE } }
        val c = withContext(Dispatchers.Default) {
            val d = app.banner ?: app.icon
            d?.let { runCatching { dominantColor(pixelsOf(it)) }.getOrNull() }
        }
        cache[app.packageName] = c ?: NONE
        return c
    }

    fun clear() = cache.clear()

    private fun pixelsOf(src: Drawable): IntArray {
        val d = src.constantState?.newDrawable()?.mutate() ?: src
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, SIZE, SIZE)
        d.draw(Canvas(bmp))
        val px = IntArray(SIZE * SIZE)
        bmp.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        bmp.recycle()
        return px
    }

    private companion object {
        const val SIZE = 24
        /** Sentinel for "computed, no colour" (fully transparent black can't be a result). */
        const val NONE = 0
    }
}
