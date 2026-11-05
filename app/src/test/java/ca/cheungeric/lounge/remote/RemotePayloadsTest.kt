package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.LayoutPrefsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePayloadsTest {
    private fun app(pkg: String, label: String) = AppEntry(pkg, label, null, null, null)
    private val apps = listOf(
        app("com.netflix.ninja", "Netflix"),
        app("com.plexapp.android", "Plex"),
        app("org.xbmc.kodi", "Kodi"),
        app("com.apple.atve", "Apple TV"),
    )

    @Test
    fun status() {
        val o = Json.parseToJsonElement(RemotePayloads.status(3, true)).jsonObject
        assertEquals("3", o["appVersion"]!!.jsonPrimitive.content)
        assertEquals("true", o["homeService"]!!.jsonPrimitive.content)
        assertEquals("timestamp", o["lastSeen"]!!.jsonObject[".sv"]!!.jsonPrimitive.content)
    }

    @Test
    fun apps_keyedByPkgKey() {
        val o = Json.parseToJsonElement(RemotePayloads.apps(apps, mapOf("com.netflix.ninja" to "abc"))).jsonObject
        assertEquals(4, o.size)
        val n = o["com,netflix,ninja"]!!.jsonObject
        assertEquals("com.netflix.ninja", n["pkg"]!!.jsonPrimitive.content)
        assertEquals("Netflix", n["label"]!!.jsonPrimitive.content)
        assertEquals("abc", n["iconHash"]!!.jsonPrimitive.content)
        assertEquals("", o["org,xbmc,kodi"]!!.jsonObject["iconHash"]!!.jsonPrimitive.content)
    }

    @Test
    fun layout_asBuilt_hiddenAlphabetical() {
        val prefs = LayoutPrefsState(
            favorites = listOf("com.plexapp.android", "com.netflix.ninja"),
            hidden = setOf("org.xbmc.kodi", "com.apple.atve", "not.installed"),
            initialized = true,
        )
        val o = Json.parseToJsonElement(RemotePayloads.layout(apps, prefs)).jsonObject
        fun list(k: String) = o[k]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("com.plexapp.android", "com.netflix.ninja"), list("favorites"))
        assertEquals(emptyList<String>(), list("others"))
        assertEquals(listOf("com.apple.atve", "org.xbmc.kodi"), list("hidden"))
    }

    @Test
    fun layout_uninitialized_appliesDefaults() {
        val o = Json.parseToJsonElement(RemotePayloads.layout(apps, LayoutPrefsState())).jsonObject
        assertEquals(listOf("com.netflix.ninja"), o["favorites"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun hashAndDataUri() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", RemotePayloads.sha1Hex("abc".toByteArray()))
        assertEquals("data:image/png;base64,AQID", RemotePayloads.pngDataUri(byteArrayOf(1, 2, 3)))
        assertEquals("\"a\\\"b\"", RemotePayloads.jsonString("a\"b"))
    }
}
