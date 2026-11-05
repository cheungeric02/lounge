package ca.cheungeric.lounge.remote.qr

/**
 * A square QR symbol. [modules] is row-major (`y * size + x`); `true` = dark.
 */
class QrMatrix(val size: Int, val modules: BooleanArray) {
    init {
        require(size > 0 && modules.size == size * size) { "modules must be size*size" }
    }

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    /** Rows of '1' (dark) / '0' (light), handy for tests and debugging. */
    fun toRows(): List<String> = (0 until size).map { y ->
        buildString(size) { for (x in 0 until size) append(if (get(x, y)) '1' else '0') }
    }
}

/**
 * Minimal pure-Kotlin QR Code encoder (ISO/IEC 18004): byte mode (UTF-8), error correction
 * level M, versions 1–6 only. Picks the smallest version that fits and the mask with the
 * lowest penalty score.
 */
object QrEncoder {

    const val MIN_VERSION = 1
    const val MAX_VERSION = 6

    /** Error correction level M format indicator bits (ISO 18004 table 12: L=01, M=00, Q=11, H=10). */
    private const val ECL_M_BITS = 0b00

    /** Per-version block structure at level M (ISO 18004 table 9). */
    internal class BlockSpec(val blocks: Int, val dataPerBlock: Int, val eccPerBlock: Int) {
        val dataCodewords: Int get() = blocks * dataPerBlock
        val totalCodewords: Int get() = blocks * (dataPerBlock + eccPerBlock)
    }

    internal val SPECS: Array<BlockSpec> = arrayOf(
        BlockSpec(1, 16, 10), // v1: 26 total
        BlockSpec(1, 28, 16), // v2: 44 total
        BlockSpec(1, 44, 26), // v3: 70 total
        BlockSpec(2, 32, 18), // v4: 100 total
        BlockSpec(2, 43, 24), // v5: 134 total
        BlockSpec(4, 27, 16), // v6: 172 total
    )

    /** Alignment pattern centre coordinates per version (ISO 18004 annex E). */
    private val ALIGNMENT: Array<IntArray> = arrayOf(
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34),
    )

    internal fun spec(version: Int): BlockSpec = SPECS[version - 1]

    /** Max byte-mode payload at M: 4-bit mode + 8-bit count (v1–9) + 8 bits per byte. */
    internal fun byteCapacity(version: Int): Int = (spec(version).dataCodewords * 8 - 12) / 8

    /** Smallest version (1–6) holding [byteCount] bytes at M, or null if none. */
    internal fun versionFor(byteCount: Int): Int? =
        (MIN_VERSION..MAX_VERSION).firstOrNull { byteCount <= byteCapacity(it) }

    fun encode(text: String): QrMatrix = encode(text, mask = null)

    /** [mask] forces a mask pattern (0–7); null evaluates all eight and keeps the lowest penalty. */
    internal fun encode(text: String, mask: Int?): QrMatrix {
        require(mask == null || mask in 0..7) { "mask must be 0..7" }
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = versionFor(bytes.size)
            ?: throw IllegalArgumentException(
                "Text is ${bytes.size} bytes; QR versions 1-$MAX_VERSION at level M hold at most ${byteCapacity(MAX_VERSION)}",
            )
        val codewords = addEcc(encodeData(bytes, version), version)
        val sym = Symbol(version)
        sym.drawFunctionPatterns()
        sym.drawCodewords(codewords)
        val chosen = mask ?: (0..7).minBy { m ->
            sym.applyMask(m)
            sym.drawFormatBits(m)
            val p = penalty(sym.size, sym.modules)
            sym.applyMask(m) // XOR again to undo
            p
        }
        sym.applyMask(chosen)
        sym.drawFormatBits(chosen)
        return QrMatrix(sym.size, sym.modules)
    }

    // ---- Data codewords -------------------------------------------------------------------

    internal fun encodeData(bytes: ByteArray, version: Int): ByteArray {
        val capacityBits = spec(version).dataCodewords * 8
        val bits = BitBuffer()
        bits.append(0b0100, 4) // byte mode
        bits.append(bytes.size, 8) // character count, 8 bits for v1–9
        for (b in bytes) bits.append(b.toInt() and 0xFF, 8)
        require(bits.length <= capacityBits) { "data too long for version $version" }
        bits.append(0, minOf(4, capacityBits - bits.length)) // terminator
        bits.append(0, (8 - bits.length % 8) % 8) // pad to byte boundary
        var pad = 0xEC
        while (bits.length < capacityBits) {
            bits.append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        return bits.toBytes()
    }

    /** Splits [data] into blocks, appends Reed–Solomon ECC per block, then interleaves. */
    internal fun addEcc(data: ByteArray, version: Int): ByteArray {
        val s = spec(version)
        require(data.size == s.dataCodewords)
        val gen = rsGenerator(s.eccPerBlock)
        val dataBlocks = (0 until s.blocks).map { b ->
            data.copyOfRange(b * s.dataPerBlock, (b + 1) * s.dataPerBlock)
        }
        val eccBlocks = dataBlocks.map { rsRemainder(it, gen) }
        val out = ByteArray(s.totalCodewords)
        var i = 0
        for (k in 0 until s.dataPerBlock) for (blk in dataBlocks) out[i++] = blk[k]
        for (k in 0 until s.eccPerBlock) for (blk in eccBlocks) out[i++] = blk[k]
        check(i == out.size)
        return out
    }

    // ---- Reed–Solomon over GF(256), primitive polynomial 0x11D --------------------------------

    internal fun gfMul(a: Int, b: Int): Int {
        var x = a
        var y = b
        var r = 0
        while (y != 0) {
            if (y and 1 != 0) r = r xor x
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
            y = y ushr 1
        }
        return r
    }

    /**
     * Generator polynomial (x - α^0)(x - α^1)…(x - α^(degree-1)), coefficients from the
     * highest power down, with the leading 1 omitted.
     */
    internal fun rsGenerator(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1 // start with the monomial 1
        var root = 1
        repeat(degree) {
            for (j in result.indices) {
                result[j] = gfMul(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = gfMul(root, 0x02)
        }
        return result
    }

    /** ECC codewords: remainder of data(x)·x^degree divided by the generator. */
    internal fun rsRemainder(data: ByteArray, generator: IntArray): ByteArray {
        val result = IntArray(generator.size)
        for (b in data) {
            val factor = (b.toInt() and 0xFF) xor result[0]
            for (j in 0 until result.size - 1) result[j] = result[j + 1]
            result[result.size - 1] = 0
            for (j in result.indices) result[j] = result[j] xor gfMul(generator[j], factor)
        }
        return ByteArray(result.size) { result[it].toByte() }
    }

    // ---- Format information ---------------------------------------------------------------

    /** 15-bit format info for level M + [mask]: BCH(15,5) with generator 0x537, XOR 0x5412. */
    internal fun formatBits(mask: Int): Int {
        val data = (ECL_M_BITS shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        return ((data shl 10) or rem) xor 0x5412
    }

    /** Positions of format bit i (bit 0 = LSB) for copy 1 (around top-left finder). */
    internal fun formatPositionsPrimary(): List<Pair<Int, Int>> = buildList {
        for (i in 0..5) add(8 to i)
        add(8 to 7)
        add(8 to 8)
        add(7 to 8)
        for (i in 9..14) add((14 - i) to 8)
    }

    /** Positions of format bit i for copy 2 (split between top-right and bottom-left). */
    internal fun formatPositionsSecondary(size: Int): List<Pair<Int, Int>> = buildList {
        for (i in 0..7) add((size - 1 - i) to 8)
        for (i in 8..14) add(8 to (size - 15 + i))
    }

    // ---- Penalty (ISO 18004 §7.8.3) -------------------------------------------------------

    private val FINDER_LIKE_A = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
    private val FINDER_LIKE_B = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)

    internal fun penalty(size: Int, m: BooleanArray): Int {
        fun at(x: Int, y: Int) = m[y * size + x]
        var score = 0
        // Rule 1: runs of 5+ same-colour modules in a row or column.
        for (horizontal in listOf(true, false)) {
            for (a in 0 until size) {
                var run = 1
                var prev = if (horizontal) at(0, a) else at(a, 0)
                for (b in 1 until size) {
                    val cur = if (horizontal) at(b, a) else at(a, b)
                    if (cur == prev) {
                        run++
                    } else {
                        if (run >= 5) score += run - 2
                        run = 1
                        prev = cur
                    }
                }
                if (run >= 5) score += run - 2
            }
        }
        // Rule 2: 2x2 blocks of one colour.
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = at(x, y)
            if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) score += 3
        }
        // Rule 3: 1:1:3:1:1 finder-like patterns with 4 light modules on one side.
        for (a in 0 until size) for (b in 0..size - 11) {
            var rowA = true
            var rowB = true
            var colA = true
            var colB = true
            for (k in 0 until 11) {
                val h = at(b + k, a)
                val v = at(a, b + k)
                if (h != FINDER_LIKE_A[k]) rowA = false
                if (h != FINDER_LIKE_B[k]) rowB = false
                if (v != FINDER_LIKE_A[k]) colA = false
                if (v != FINDER_LIKE_B[k]) colB = false
            }
            if (rowA) score += 40
            if (rowB) score += 40
            if (colA) score += 40
            if (colB) score += 40
        }
        // Rule 4: dark-module proportion, 10 points per 5% away from 50%.
        val dark = m.count { it }
        val total = size * size
        val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        score += k * 10
        return score
    }

    private fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0
        5 -> x * y % 2 + x * y % 3 == 0
        6 -> (x * y % 2 + x * y % 3) % 2 == 0
        7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
        else -> error("bad mask")
    }

    // ---- Symbol construction --------------------------------------------------------------

    private class Symbol(val version: Int) {
        val size = version * 4 + 17
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y * size + x] = dark
            isFunction[y * size + x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val pos = ALIGNMENT[version - 1]
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                val overlapsFinder = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)
                if (!overlapsFinder) alignment(pos[i], pos[j])
            }
            drawFormatBits(0) // reserve the format areas (overwritten after masking)
        }

        private fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    val d = maxOf(Math.abs(dx), Math.abs(dy))
                    set(x, y, d != 2 && d != 4)
                }
            }
        }

        private fun alignment(cx: Int, cy: Int) {
            for (dy in -2..2) for (dx in -2..2) {
                set(cx + dx, cy + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
        }

        fun drawFormatBits(mask: Int) {
            val bits = formatBits(mask)
            val p1 = formatPositionsPrimary()
            val p2 = formatPositionsSecondary(size)
            for (i in 0 until 15) {
                val dark = (bits ushr i) and 1 != 0
                set(p1[i].first, p1[i].second, dark)
                set(p2[i].first, p2[i].second, dark)
            }
            set(8, size - 8, true) // dark module
        }

        /** Zig-zag placement from the bottom-right, two columns at a time, skipping column 6. */
        fun drawCodewords(data: ByteArray) {
            val totalBits = data.size * 8
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                val upward = ((right + 1) and 2) == 0
                for (vert in 0 until size) {
                    val y = if (upward) size - 1 - vert else vert
                    for (j in 0..1) {
                        val x = right - j
                        val idx = y * size + x
                        if (!isFunction[idx] && i < totalBits) {
                            modules[idx] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                        // Remainder bits (7 for v2–6) stay light, as the spec requires.
                    }
                }
                right -= 2
            }
            check(i == totalBits)
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val idx = y * size + x
                if (!isFunction[idx] && maskBit(mask, x, y)) modules[idx] = !modules[idx]
            }
        }
    }

    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val length: Int get() = bits.size

        fun append(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 != 0)
        }

        fun toBytes(): ByteArray {
            check(bits.size % 8 == 0)
            return ByteArray(bits.size / 8) { b ->
                var v = 0
                for (k in 0 until 8) v = (v shl 1) or (if (bits[b * 8 + k]) 1 else 0)
                v.toByte()
            }
        }
    }
}
