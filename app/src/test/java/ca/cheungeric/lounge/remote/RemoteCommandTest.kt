package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandTest {

    private val now = 1_800_000_000_000L
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun fresh(s: String) = RemoteCommand.parseFresh(j(s), now)

    @Test
    fun everyType() {
        val p = "com.netflix.ninja"
        assertEquals(RemoteCommand.Launch(p), fresh("""{"type":"launch","pkg":"$p","at":$now}"""))
        assertEquals(RemoteCommand.Favorite(p), fresh("""{"type":"favorite","pkg":"$p","at":$now}"""))
        assertEquals(RemoteCommand.Unfavorite(p), fresh("""{"type":"unfavorite","pkg":"$p","at":$now}"""))
        assertEquals(RemoteCommand.Hide(p), fresh("""{"type":"hide","pkg":"$p","at":$now}"""))
        assertEquals(RemoteCommand.Unhide(p), fresh("""{"type":"unhide","pkg":"$p","at":$now}"""))
        assertEquals(RemoteCommand.MoveFavorite(p, 2), fresh("""{"type":"moveFavorite","pkg":"$p","to":2,"at":$now}"""))
        assertEquals(RemoteCommand.Home, fresh("""{"type":"home","at":$now}"""))
        assertEquals(RemoteCommand.Back, fresh("""{"type":"back","at":$now}"""))
    }

    @Test
    fun freshnessWindow() {
        assertTrue(RemoteCommand.isFresh(now - 30_000, now))
        assertTrue(RemoteCommand.isFresh(now + 30_000, now))
        assertFalse(RemoteCommand.isFresh(now - 30_001, now))
        assertFalse(RemoteCommand.isFresh(now + 30_001, now))
        assertNull(fresh("""{"type":"home","at":${now - 31_000}}"""))
        assertNull(fresh("""{"type":"home","at":${now + 31_000}}"""))
    }

    @Test
    fun missingOrNonNumericAt_dropped() {
        assertNull(fresh("""{"type":"home"}"""))
        assertNull(fresh("""{"type":"home","at":"$now"}"""))
        assertNull(fresh("""{"type":"home","at":null}"""))
        assertNull(fresh("""{"type":"home","at":{".sv":"timestamp"}}"""))
        assertNull(fresh("""{"type":"home","at":true}"""))
        // Parsing ignores `at`; only parseFresh requires it.
        assertEquals(RemoteCommand.Home, RemoteCommand.parse(j("""{"type":"home"}""")))
    }

    @Test
    fun unknownOrMissingType_dropped() {
        assertNull(fresh("""{"type":"reboot","at":$now}"""))
        assertNull(fresh("""{"pkg":"com.a.b","at":$now}"""))
        assertNull(fresh("""{"type":7,"at":$now}"""))
        assertNull(RemoteCommand.parse(j("[1,2]")))
        assertNull(RemoteCommand.parse(j("null")))
        assertNull(RemoteCommand.parse(null))
    }

    @Test
    fun badPkg_dropped() {
        for (bad in listOf("", "netflix", "com..netflix", ".com.a", "com.a.", "com/a.b", "1com.a", "com.a b")) {
            assertNull(bad, fresh("""{"type":"launch","pkg":"$bad","at":$now}"""))
        }
        assertNull(fresh("""{"type":"launch","at":$now}"""))
        assertNull(fresh("""{"type":"launch","pkg":5,"at":$now}"""))
        assertNull(fresh("""{"type":"hide","pkg":"${"a.".repeat(130)}b","at":$now}"""))
    }

    @Test
    fun moveFavorite_needsIntegerTo() {
        val p = "com.a.b"
        assertNull(fresh("""{"type":"moveFavorite","pkg":"$p","at":$now}"""))
        assertNull(fresh("""{"type":"moveFavorite","pkg":"$p","to":"1","at":$now}"""))
        assertNull(fresh("""{"type":"moveFavorite","pkg":"$p","to":1.5,"at":$now}"""))
        assertEquals(RemoteCommand.MoveFavorite(p, -3), fresh("""{"type":"moveFavorite","pkg":"$p","to":-3,"at":$now}"""))
        assertEquals(RemoteCommand.MoveFavorite(p, 1), fresh("""{"type":"moveFavorite","pkg":"$p","to":1.0,"at":$now}"""))
    }

    @Test
    fun pkgKey_roundTrip() {
        assertEquals("com,netflix,ninja", RemoteCommand.pkgKey("com.netflix.ninja"))
        assertEquals("com.netflix.ninja", RemoteCommand.pkgFromKey("com,netflix,ninja"))
    }

    @Test
    fun childrenOf_events() {
        val all = RemoteCommand.childrenOf(SseEvent.Put("/", """{"-a":{"type":"home"},"-b":{"type":"back"}}"""))
        assertEquals(listOf("-a", "-b"), all.map { it.first })
        assertEquals(listOf("-c"), RemoteCommand.childrenOf(SseEvent.Put("/-c", """{"type":"home"}""")).map { it.first })
        assertTrue(RemoteCommand.childrenOf(SseEvent.Put("/-c", "null")).isEmpty())
        assertTrue(RemoteCommand.childrenOf(SseEvent.Put("/", "null")).isEmpty())
        assertTrue(RemoteCommand.childrenOf(SseEvent.Put("/-c/type", "\"home\"")).isEmpty())
        assertEquals(listOf("-d"), RemoteCommand.childrenOf(SseEvent.Patch("/", """{"-d":{"type":"home"},"-e":null}""")).map { it.first })
        assertTrue(RemoteCommand.childrenOf(SseEvent.KeepAlive).isEmpty())
    }
}
