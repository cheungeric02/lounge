package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TvInputTest {

    private fun node(
        present: Boolean = true,
        editable: Boolean = true,
        focused: Boolean = true,
        password: Boolean = false,
        hint: String? = "Search",
        text: String? = "star",
        showingHint: Boolean = false,
    ) = TvInput.fromNode(present, editable, focused, password, "com.google.android.youtube.tv", hint, text, showingHint)

    @Test
    fun focusedEditable_isActive() {
        assertEquals(InputSnapshot(true, "com.google.android.youtube.tv", "Search", "star"), node())
        assertEquals(InputSnapshot(true, "com.google.android.youtube.tv", "", ""), node(hint = null, text = null))
    }

    @Test
    fun neverReadsPasswordOrNonEditable() {
        assertEquals(InputSnapshot.NONE, node(password = true))
        assertEquals(InputSnapshot.NONE, node(editable = false))
        assertEquals(InputSnapshot.NONE, node(focused = false))
        assertEquals(InputSnapshot.NONE, node(present = false))
    }

    @Test
    fun hintShownAsText_isEmpty() {
        assertEquals("", node(text = "Search", showingHint = true).text)
    }

    @Test
    fun truncation() {
        assertEquals(TvInput.MAX_TEXT, node(text = "x".repeat(800)).text.length)
        assertEquals(200, node(hint = "h".repeat(300)).hint.length)
    }

    @Test
    fun payload() {
        val o = Json.parseToJsonElement(TvInput.payload(node())).jsonObject
        assertEquals("true", o["active"]!!.jsonPrimitive.content)
        assertEquals("com.google.android.youtube.tv", o["pkg"]!!.jsonPrimitive.content)
        assertEquals("Search", o["hint"]!!.jsonPrimitive.content)
        assertEquals("star", o["text"]!!.jsonPrimitive.content)
        val n = Json.parseToJsonElement(TvInput.payload(InputSnapshot(false, "p", "h", "secret"))).jsonObject
        assertEquals("false", n["active"]!!.jsonPrimitive.content)
        assertEquals("", n["pkg"]!!.jsonPrimitive.content)
        assertEquals("", n["text"]!!.jsonPrimitive.content)
        assertEquals(4, n.size)
    }
}
