package ca.cheungeric.lounge.ui.home

/** Pixels below this alpha are ignored. */
private const val MIN_ALPHA = 128
private const val MIN_SATURATION = 0.25f
private const val MIN_LIGHTNESS = 0.12f
private const val MAX_LIGHTNESS = 0.90f
private const val HUE_BUCKETS = 12

/**
 * Dominant "brand" colour of an icon/banner (SPEC "v6 → Tint to the focused app"). Pure: takes ARGB
 * pixels (e.g. a ~24×24 downscale). Ignores translucent, greyish, very dark and very light pixels,
 * buckets the rest by hue (30° each) and returns the average colour (opaque) of the most common
 * bucket, or null when nothing colourful is left. Ties go to the lower hue bucket.
 */
fun dominantColor(pixels: IntArray): Int? {
    val count = IntArray(HUE_BUCKETS)
    val sumR = LongArray(HUE_BUCKETS)
    val sumG = LongArray(HUE_BUCKETS)
    val sumB = LongArray(HUE_BUCKETS)
    for (p in pixels) {
        if ((p ushr 24) and 0xFF < MIN_ALPHA) continue
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 510f
        if (lightness < MIN_LIGHTNESS || lightness > MAX_LIGHTNESS) continue
        val delta = (max - min) / 255f
        // HSL saturation.
        val sat = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        if (sat < MIN_SATURATION) continue
        val bucket = (hueOf(r, g, b, max, min) / (360f / HUE_BUCKETS)).toInt().coerceIn(0, HUE_BUCKETS - 1)
        count[bucket]++
        sumR[bucket] += r.toLong()
        sumG[bucket] += g.toLong()
        sumB[bucket] += b.toLong()
    }
    var best = -1
    for (i in 0 until HUE_BUCKETS) if (count[i] > 0 && (best < 0 || count[i] > count[best])) best = i
    if (best < 0) return null
    val n = count[best]
    val r = (sumR[best] / n).toInt()
    val g = (sumG[best] / n).toInt()
    val b = (sumB[best] / n).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** Hue in degrees 0..<360 (max != min). */
private fun hueOf(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
    val d = (max - min).toFloat()
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return if (h < 0f) h + 360f else h
}
