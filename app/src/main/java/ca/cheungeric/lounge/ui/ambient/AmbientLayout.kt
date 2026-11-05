package ca.cheungeric.lounge.ui.ambient

/** Top-left position of the ambient block inside the free area. */
data class AmbientOffset(val x: Int, val y: Int)

/** Pure burn-in shift: a deterministic pseudo-random position per wall-clock minute. */
object AmbientLayout {

    /** Fraction of each screen edge kept clear (TV overscan safe area). */
    const val SAFE_MARGIN_FRACTION = 0.05f

    /**
     * Offset for [minuteIndex] (minutes since the epoch) within a free area of [freeW] x [freeH] px
     * (container size minus block size minus margins). Always in 0..freeW / 0..freeH; 0 when the
     * block does not fit. Consecutive minutes never repeat the same position when there is room:
     * even minutes use even x (or y when x has no room), odd minutes odd.
     */
    fun offsetFor(minuteIndex: Long, freeW: Int, freeH: Int): AmbientOffset {
        val w = freeW.coerceAtLeast(0)
        val h = freeH.coerceAtLeast(0)
        val a = mix(minuteIndex)
        val b = mix(a)
        val parity = (minuteIndex and 1L).toInt()
        return when {
            w >= 1 -> AmbientOffset(pick(a, w, parity), pick(b, h, null))
            h >= 1 -> AmbientOffset(0, pick(b, h, parity))
            else -> AmbientOffset(0, 0)
        }
    }

    /** Uniform value in 0..max from [hash]; restricted to values of [parity] when given (max >= 1). */
    private fun pick(hash: Long, max: Int, parity: Int?): Int {
        if (max <= 0) return 0
        if (parity == null) return java.lang.Long.remainderUnsigned(hash, (max + 1).toLong()).toInt()
        val count = (max - parity) / 2 + 1
        return parity + 2 * java.lang.Long.remainderUnsigned(hash, count.toLong()).toInt()
    }

    /** SplitMix64 finalizer. */
    private fun mix(v: Long): Long {
        var z = v + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
