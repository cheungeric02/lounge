package ca.cheungeric.lounge.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun isNewer_onlyWhenStrictlyGreater() {
        assertTrue(isNewer(3, 2))
        assertFalse(isNewer(2, 2))
        assertFalse(isNewer(1, 2))
    }

    @Test
    fun parsesVersionJson() {
        val info = parseVersionInfo(
            """{ "versionCode": 3, "versionName": "v3", "apkUrl": "https://cheungeric02.github.io/lounge/lounge.apk", "sha256": "ABC" }""",
        )
        assertEquals(VersionInfo(3, "v3", "https://cheungeric02.github.io/lounge/lounge.apk", "ABC"), info)
    }

    @Test
    fun parsesVersionJsonIgnoringUnknownKeys() {
        val info = parseVersionInfo(
            """{"versionCode":4,"versionName":"v4","apkUrl":"u","sha256":"s","releasedAt":"2026-11-04","extra":{"a":[1]}}""",
        )
        assertNotNull(info)
        assertEquals(4, info!!.versionCode)
    }

    @Test
    fun malformedVersionJsonIsNull() {
        assertNull(parseVersionInfo("not json"))
        assertNull(parseVersionInfo("""{"versionCode":4}"""))
        assertNull(parseVersionInfo("""{"versionCode":"x","versionName":"v","apkUrl":"u","sha256":"s"}"""))
        assertNull(parseVersionInfo(""))
    }

    @Test
    fun checkerIsUpdateComparesAgainstLocalCode() {
        val checker = UpdateChecker(localVersionCode = 2)
        assertTrue(checker.isUpdate(VersionInfo(3, "v3", "u", "s")))
        assertFalse(checker.isUpdate(VersionInfo(2, "v2", "u", "s")))
    }

    @Test
    fun sha256HexOfKnownInput() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0).inputStream()),
        )
    }

    @Test
    fun sha256StreamMatchesBytesAcrossBufferBoundary() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(sha256Hex(data), sha256Hex(data.inputStream()))
    }

    @Test
    fun sha256MatchIsCaseInsensitiveAndRejectsBlank() {
        val h = sha256Hex("abc".toByteArray())
        assertTrue(sha256Matches(h.uppercase(), h))
        assertTrue(sha256Matches(" $h\n", h))
        assertFalse(sha256Matches("", h))
        assertFalse(sha256Matches(h.replaceFirst('b', 'c'), h))
    }
}
