package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ca.cheungeric.lounge.remote.RemoteCommand.MediaAction as M
import ca.cheungeric.lounge.remote.RemoteCommand.VolumeAction as V

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

    // ---- v3 ----

    @Test
    fun v3_everyType() {
        assertEquals(RemoteCommand.Media(M.Toggle), fresh("""{"type":"media","action":"toggle","at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Play), fresh("""{"type":"media","action":"play","at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Pause), fresh("""{"type":"media","action":"pause","at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Next), fresh("""{"type":"media","action":"next","at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Prev), fresh("""{"type":"media","action":"prev","at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Seek, 10_000), fresh("""{"type":"media","action":"seek","ms":10000,"at":$now}"""))
        assertEquals(RemoteCommand.Media(M.Seek, -10_000), fresh("""{"type":"media","action":"seek","ms":-10000,"at":$now}"""))
        // ms ignored for non-seek actions.
        assertEquals(RemoteCommand.Media(M.Play), fresh("""{"type":"media","action":"play","ms":5,"at":$now}"""))
        assertEquals(RemoteCommand.Volume(V.Up), fresh("""{"type":"volume","action":"up","at":$now}"""))
        assertEquals(RemoteCommand.Volume(V.Down), fresh("""{"type":"volume","action":"down","at":$now}"""))
        assertEquals(RemoteCommand.Volume(V.Mute), fresh("""{"type":"volume","action":"mute","at":$now}"""))
        assertEquals(RemoteCommand.Sleep(0), fresh("""{"type":"sleep","minutes":0,"at":$now}"""))
        assertEquals(RemoteCommand.Sleep(30), fresh("""{"type":"sleep","minutes":30,"at":$now}"""))
        assertEquals(RemoteCommand.Sleep(240), fresh("""{"type":"sleep","minutes":240.0,"at":$now}"""))
        assertEquals(
            RemoteCommand.Open("https://youtu.be/dQw4w9WgXcQ"),
            fresh("""{"type":"open","url":"  https://youtu.be/dQw4w9WgXcQ ","at":$now}"""),
        )
        assertEquals(RemoteCommand.Text("hello \"world\""), fresh("""{"type":"text","text":"hello \"world\"","at":$now}"""))
        assertEquals(RemoteCommand.Text(""), fresh("""{"type":"text","text":"","at":$now}"""))
        assertEquals(RemoteCommand.Submit, fresh("""{"type":"submit","at":$now}"""))
    }

    @Test
    fun v3_needFreshness() {
        assertNull(fresh("""{"type":"media","action":"toggle","at":${now - 31_000}}"""))
        assertNull(fresh("""{"type":"volume","action":"up"}"""))
        assertNull(fresh("""{"type":"submit","at":"$now"}"""))
    }

    @Test
    fun media_invalid() {
        for (bad in listOf(
            """{"type":"media","at":$now}""",
            """{"type":"media","action":"stop","at":$now}""",
            """{"type":"media","action":"TOGGLE","at":$now}""",
            """{"type":"media","action":5,"at":$now}""",
            """{"type":"media","action":"seek","at":$now}""",
            """{"type":"media","action":"seek","ms":0,"at":$now}""",
            """{"type":"media","action":"seek","ms":"10000","at":$now}""",
            """{"type":"media","action":"seek","ms":1.5,"at":$now}""",
            """{"type":"media","action":"seek","ms":true,"at":$now}""",
            """{"type":"media","action":"seek","ms":${RemoteCommand.MAX_SEEK_MS + 1},"at":$now}""",
            """{"type":"media","action":"seek","ms":${-RemoteCommand.MAX_SEEK_MS - 1},"at":$now}""",
        )) {
            assertNull(bad, fresh(bad))
        }
        assertEquals(
            RemoteCommand.Media(RemoteCommand.MediaAction.Seek, RemoteCommand.MAX_SEEK_MS),
            fresh("""{"type":"media","action":"seek","ms":${RemoteCommand.MAX_SEEK_MS},"at":$now}"""),
        )
    }

    @Test
    fun volume_invalid() {
        assertNull(fresh("""{"type":"volume","at":$now}"""))
        assertNull(fresh("""{"type":"volume","action":"max","at":$now}"""))
        assertNull(fresh("""{"type":"volume","action":1,"at":$now}"""))
    }

    @Test
    fun sleep_invalid() {
        for (m in listOf("-1", "241", "1.5", "\"30\"", "null", "true", "1e12")) {
            val js = """{"type":"sleep","minutes":$m,"at":$now}"""
            assertNull(js, fresh(js))
        }
        assertNull(fresh("""{"type":"sleep","at":$now}"""))
    }

    @Test
    fun open_invalid() {
        for (u in listOf("ftp://example.com/", "javascript:alert(1)", "intent://x#Intent;end", "file:///x", "example.com", "")) {
            val js = """{"type":"open","url":"$u","at":$now}"""
            assertNull(js, fresh(js))
        }
        val long = "https://example.com/" + "a".repeat(LinkRouter.MAX_LEN)
        assertNull(fresh("""{"type":"open","url":"$long","at":$now}"""))
        assertNull(fresh("""{"type":"open","url":5,"at":$now}"""))
        assertNull(fresh("""{"type":"open","at":$now}"""))
    }

    @Test
    fun text_invalid() {
        assertNull(fresh("""{"type":"text","at":$now}"""))
        assertNull(fresh("""{"type":"text","text":5,"at":$now}"""))
        assertNull(fresh("""{"type":"text","text":null,"at":$now}"""))
        val max = "a".repeat(TvInput.MAX_TEXT)
        assertEquals(RemoteCommand.Text(max), fresh("""{"type":"text","text":"$max","at":$now}"""))
        assertNull(fresh("""{"type":"text","text":"${max}b","at":$now}"""))
    }
}
