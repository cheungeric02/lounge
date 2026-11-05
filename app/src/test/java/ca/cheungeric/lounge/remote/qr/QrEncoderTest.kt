package ca.cheungeric.lounge.remote.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QrEncoderTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    // ---- Reed–Solomon ---------------------------------------------------------------------

    @Test fun rsMatchesThonkyHelloWorld1M() {
        // Thonky QR tutorial: "HELLO WORLD" (alphanumeric) at 1-M.
        val data = bytes(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        val ecc = QrEncoder.rsRemainder(data, QrEncoder.rsGenerator(10))
        assertArrayEquals(bytes(196, 35, 39, 119, 235, 215, 231, 226, 93, 23), ecc)
    }

    @Test fun rsGeneratorDegree2() {
        // (x - 1)(x - 2) = x^2 + 3x + 2 over GF(256).
        assertArrayEquals(intArrayOf(3, 2), QrEncoder.rsGenerator(2))
    }

    @Test fun gfMulWrapsWithPrimitivePoly() {
        assertEquals(0x1D, QrEncoder.gfMul(0x80, 0x02))
        assertEquals(1, QrEncoder.gfMul(1, 1))
        assertEquals(0, QrEncoder.gfMul(0, 0xFF))
    }

    // ---- Block tables / version selection ---------------------------------------------------

    @Test fun blockSpecMatchesIsoTotals() {
        val totals = (1..6).map { QrEncoder.spec(it).totalCodewords }
        assertEquals(listOf(26, 44, 70, 100, 134, 172), totals)
        val ecc = (1..6).map { QrEncoder.spec(it).eccPerBlock }
        assertEquals(listOf(10, 16, 26, 18, 24, 16), ecc)
        val data = (1..6).map { QrEncoder.spec(it).dataCodewords }
        assertEquals(listOf(16, 28, 44, 64, 86, 108), data)
    }

    @Test fun versionSelectionBoundaries() {
        val limits = listOf(14, 26, 42, 62, 84, 106)
        limits.forEachIndexed { i, max ->
            val v = i + 1
            assertEquals("max for v$v", v, QrEncoder.versionFor(max))
            if (v < 6) assertEquals("max+1 for v$v", v + 1, QrEncoder.versionFor(max + 1))
        }
        assertEquals(1, QrEncoder.versionFor(0))
        assertNull(QrEncoder.versionFor(107))
    }

    @Test fun encodeSizesFollowVersion() {
        assertEquals(21, QrEncoder.encode("a".repeat(14)).size)
        assertEquals(25, QrEncoder.encode("a".repeat(15)).size)
        assertEquals(41, QrEncoder.encode("a".repeat(106)).size)
    }

    @Test fun utf8BytesCountTowardsCapacity() {
        // "é" is 2 bytes in UTF-8: 7 chars = 14 bytes fits v1, 8 chars = 16 bytes needs v2.
        assertEquals(21, QrEncoder.encode("é".repeat(7)).size)
        assertEquals(25, QrEncoder.encode("é".repeat(8)).size)
    }

    @Test fun tooLongThrows() {
        try {
            QrEncoder.encode("a".repeat(107))
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun dataPaddingAlternatesEc11() {
        val data = QrEncoder.encodeData("A".toByteArray(), 1)
        assertEquals(16, data.size)
        // 0100 00000001 01000001 0000 -> 0x40 0x14 0x10, then EC 11 EC ...
        assertEquals(0x40, data[0].toInt() and 0xFF)
        assertEquals(0x14, data[1].toInt() and 0xFF)
        assertEquals(0x10, data[2].toInt() and 0xFF)
        assertEquals(0xEC, data[3].toInt() and 0xFF)
        assertEquals(0x11, data[4].toInt() and 0xFF)
        assertEquals(0xEC, data[5].toInt() and 0xFF)
    }

    // ---- Format info ---------------------------------------------------------------------

    @Test fun formatBitsForLevelM() {
        // ISO 18004 table C.1 / Thonky format-information table, level M, masks 0..7.
        val expected = listOf(
            "101010000010010", "101000100100101", "101111001111100", "101101101001011",
            "100010111111001", "100000011001110", "100111110010111", "100101010100000",
        )
        for (m in 0..7) {
            assertEquals("mask $m", expected[m], QrEncoder.formatBits(m).toString(2).padStart(15, '0'))
        }
    }

    private fun readFormat(m: QrMatrix, positions: List<Pair<Int, Int>>): Int {
        var v = 0
        positions.forEachIndexed { i, (x, y) -> if (m[x, y]) v = v or (1 shl i) }
        return v
    }

    @Test fun formatInfoReadsBackValidAndAgrees() {
        val validM = (0..7).associateBy { QrEncoder.formatBits(it) }
        for (text in listOf("Lounge", QrFixtures.URL_TEXT, QrFixtures.V6_TEXT)) {
            val m = QrEncoder.encode(text)
            val a = readFormat(m, QrEncoder.formatPositionsPrimary())
            val b = readFormat(m, QrEncoder.formatPositionsSecondary(m.size))
            assertEquals("copies agree for $text", a, b)
            assertNotNull("valid M format word for $text", validM[a])
        }
        for (mask in 0..7) {
            val m = QrEncoder.encode(QrFixtures.URL_TEXT, mask)
            assertEquals(mask, validM[readFormat(m, QrEncoder.formatPositionsPrimary())])
        }
    }

    // ---- Structure -----------------------------------------------------------------------

    private fun assertFinder(m: QrMatrix, left: Int, top: Int) {
        for (dy in -1..7) for (dx in -1..7) {
            val x = left + dx
            val y = top + dy
            if (x !in 0 until m.size || y !in 0 until m.size) continue
            val d = maxOf(Math.abs(dx - 3), Math.abs(dy - 3))
            val expectDark = d != 2 && d != 4
            assertEquals("finder at ($x,$y)", expectDark, m[x, y])
        }
    }

    @Test fun structuralPatterns() {
        for (text in listOf("Lounge", QrFixtures.V3_TEXT, QrFixtures.URL_TEXT, QrFixtures.V6_TEXT)) {
            val m = QrEncoder.encode(text)
            val n = m.size
            assertFinder(m, 0, 0)
            assertFinder(m, n - 7, 0)
            assertFinder(m, 0, n - 7)
            for (i in 8 until n - 8) {
                assertEquals("timing row $i", i % 2 == 0, m[i, 6])
                assertEquals("timing col $i", i % 2 == 0, m[6, i])
            }
            assertTrue("dark module", m[8, n - 8])
            if (n > 21) {
                val c = n - 7 // bottom-right alignment centre for v2–6
                for (dy in -2..2) for (dx in -2..2) {
                    assertEquals("alignment", maxOf(Math.abs(dx), Math.abs(dy)) != 1, m[c + dx, c + dy])
                }
            }
        }
    }

    // ---- Reference fixtures (segno) --------------------------------------------------------

    @Test fun matchesSegnoForEveryMask() {
        QrFixtures.URL_MASKS.forEachIndexed { mask, expected ->
            assertEquals("mask $mask", expected, QrEncoder.encode(QrFixtures.URL_TEXT, mask).toRows())
        }
    }

    @Test fun otherVersionsMatchSegnoAtSegnosMask() {
        // Forces segno's chosen mask so this checks everything except the mask choice itself
        // (segno scores masks before drawing format info; this encoder scores the final symbol).
        assertEquals(QrFixtures.SHORT_AUTO, QrEncoder.encode(QrFixtures.SHORT_TEXT, QrFixtures.SHORT_AUTO_MASK).toRows())
        assertEquals(QrFixtures.V3_AUTO, QrEncoder.encode(QrFixtures.V3_TEXT, QrFixtures.V3_AUTO_MASK).toRows())
        assertEquals(QrFixtures.V6_AUTO, QrEncoder.encode(QrFixtures.V6_TEXT, QrFixtures.V6_AUTO_MASK).toRows())
    }

    @Test fun urlAutoMaskMatchesSegno() {
        assertEquals(QrFixtures.URL_MASKS[QrFixtures.URL_AUTO_MASK], QrEncoder.encode(QrFixtures.URL_TEXT).toRows())
    }

    @Test fun autoMaskIsLowestPenalty() {
        for (text in listOf(QrFixtures.SHORT_TEXT, QrFixtures.V3_TEXT, QrFixtures.URL_TEXT, QrFixtures.V6_TEXT)) {
            val scores = (0..7).map { m ->
                val q = QrEncoder.encode(text, m)
                QrEncoder.penalty(q.size, q.modules)
            }
            val auto = QrEncoder.encode(text)
            val best = scores.indexOf(scores.min())
            assertEquals("auto mask for $text", QrEncoder.encode(text, best).toRows(), auto.toRows())
        }
    }

    @Test fun penaltyRulesOnTinyMatrices() {
        // All-light 11x11: N1 = 22 lines * (11-2) = 198, N2 = 100 * 3 = 300, N4 = 10 * 9 = 90.
        // N3: no dark modules, so no finder-like pattern.
        assertEquals(198 + 300 + 90, QrEncoder.penalty(11, BooleanArray(121)))
    }

    @Test fun matrixGetIsRowMajor() {
        val m = QrMatrix(2, booleanArrayOf(false, true, false, false))
        assertTrue(m[1, 0])
        assertEquals(listOf("01", "00"), m.toRows())
    }
}
