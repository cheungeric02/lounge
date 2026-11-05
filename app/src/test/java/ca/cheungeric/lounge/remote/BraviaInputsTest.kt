package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BraviaInputsTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private fun arr(s: String) = Json.parseToJsonElement(s) as JsonArray

    @Test fun externalInputsStatus_body() {
        val b = obj(BraviaRpc.getCurrentExternalInputsStatus())
        assertEquals("getCurrentExternalInputsStatus", b["method"]!!.jsonPrimitive.content)
        assertEquals("1.1", b["version"]!!.jsonPrimitive.content)
        assertEquals(105, b["id"]!!.jsonPrimitive.content.toInt())
        assertEquals(0, b["params"]!!.jsonArray.size)
        assertEquals("1.0", obj(BraviaRpc.getCurrentExternalInputsStatus("1.0"))["version"]!!.jsonPrimitive.content)

        val p = obj(BraviaRpc.getPlayingContentInfo())
        assertEquals("getPlayingContentInfo", p["method"]!!.jsonPrimitive.content)
        assertEquals(103, p["id"]!!.jsonPrimitive.content.toInt())
        assertEquals("1.0", p["version"]!!.jsonPrimitive.content)
    }

    @Test fun setPlayContent_uri() {
        val b = obj(BraviaRpc.setPlayContent("extInput:hdmi?port=3"))
        assertEquals("setPlayContent", b["method"]!!.jsonPrimitive.content)
        assertEquals("extInput:hdmi?port=3", b["params"]!!.jsonArray[0].jsonObject["uri"]!!.jsonPrimitive.content)
        // The port overload delegates to the same body.
        assertEquals(BraviaRpc.setPlayContent("extInput:hdmi?port=2"), BraviaRpc.setPlayContent(2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPlayContent_rejectsNonHdmi() {
        BraviaRpc.setPlayContent("tv:dvbt")
    }

    @Test fun hdmiInputsOf_parsesFiltersAndSorts() {
        val result = arr(
            """[[
              {"uri":"extInput:hdmi?port=3","title":"HDMI 3","label":"Switch","connection":true,"icon":"x"},
              {"uri":"extInput:cec?type=player&port=3&logicalAddr=4","title":"Player","label":"","connection":true,"icon":""},
              {"uri":"extInput:hdmi?port=1","title":"HDMI 1","label":"  ","connection":false,"icon":"","status":"false"},
              {"uri":"extInput:composite?port=1","title":"Video","label":"","connection":false,"icon":""},
              {"uri":"extInput:hdmi?port=2","title":"","label":"","connection":true,"icon":""}
            ]]""",
        )
        val inputs = BraviaRpc.hdmiInputsOf(result)
        assertEquals(listOf(1, 2, 3), inputs.map { it.port })
        assertEquals(listOf("HDMI 1", "HDMI 2", "Switch"), inputs.map { it.displayName })
        assertFalse(inputs[0].connected)
        assertTrue(inputs[1].connected)
        assertEquals("extInput:hdmi?port=3", inputs[2].uri)
    }

    @Test fun hdmiInputsOf_badShapes() {
        assertTrue(BraviaRpc.hdmiInputsOf(arr("[]")).isEmpty())
        assertTrue(BraviaRpc.hdmiInputsOf(arr("""[{"uri":"x"}]""")).isEmpty())
        assertTrue(BraviaRpc.hdmiInputsOf(arr("""[[{"uri":"extInput:hdmi"}, 5, {"title":"no uri"}]]""")).isEmpty())
        // String "true" is not a boolean connection.
        val s = BraviaRpc.hdmiInputsOf(arr("""[[{"uri":"extInput:hdmi?port=4","connection":"true"}]]"""))
        assertFalse(s.single().connected)
    }

    @Test fun playingUriOf() {
        assertEquals(
            "extInput:hdmi?port=2",
            BraviaRpc.playingUriOf(arr("""[{"uri":"extInput:hdmi?port=2","source":"extInput:hdmi","title":"HDMI 2"}]""")),
        )
        assertNull(BraviaRpc.playingUriOf(arr("[]")))
        assertNull(BraviaRpc.playingUriOf(arr("""[{"source":"app"}]""")))
        assertNull(BraviaRpc.playingUriOf(arr("""[{"uri":""}]""")))
    }
}
