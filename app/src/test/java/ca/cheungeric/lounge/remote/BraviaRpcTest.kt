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
import ca.cheungeric.lounge.remote.BraviaRpc.PowerState
import ca.cheungeric.lounge.remote.BraviaRpc.Reply

class BraviaRpcTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun requests_shape() {
        val g = obj(BraviaRpc.getPowerStatus())
        assertEquals("getPowerStatus", g["method"]!!.jsonPrimitive.content)
        assertEquals("1.0", g["version"]!!.jsonPrimitive.content)
        assertEquals(0, g["params"]!!.jsonArray.size)
        assertTrue(g.containsKey("id"))

        val on = obj(BraviaRpc.setPowerStatus(true))
        assertEquals("setPowerStatus", on["method"]!!.jsonPrimitive.content)
        assertEquals("true", on["params"]!!.jsonArray[0].jsonObject["status"]!!.jsonPrimitive.content)
        assertFalse(on["params"]!!.jsonArray[0].jsonObject["status"]!!.jsonPrimitive.isString)
        val off = obj(BraviaRpc.setPowerStatus(false))
        assertEquals("false", off["params"]!!.jsonArray[0].jsonObject["status"]!!.jsonPrimitive.content)

        val pc = obj(BraviaRpc.setPlayContent(3))
        assertEquals("setPlayContent", pc["method"]!!.jsonPrimitive.content)
        assertEquals("extInput:hdmi?port=3", pc["params"]!!.jsonArray[0].jsonObject["uri"]!!.jsonPrimitive.content)

        val si = obj(BraviaRpc.getSystemInformation())
        assertEquals("getSystemInformation", si["method"]!!.jsonPrimitive.content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun setPlayContent_badPort() {
        BraviaRpc.setPlayContent(5)
    }

    @Test fun parse_powerStatus() {
        val a = BraviaRpc.parseReply(200, """{"result":[{"status":"active"}],"id":50}""")
        assertEquals(PowerState.On, BraviaRpc.powerStateOf((a as Reply.Ok).result))
        val s = BraviaRpc.parseReply(200, """{"result":[{"status":"standby"}],"id":50}""")
        assertEquals(PowerState.Standby, BraviaRpc.powerStateOf((s as Reply.Ok).result))
        assertEquals(PowerState.Unknown, BraviaRpc.powerStateOf(JsonArray(emptyList())))
        val w = BraviaRpc.parseReply(200, """{"result":[{"status":"weird"}]}""")
        assertEquals(PowerState.Unknown, BraviaRpc.powerStateOf((w as Reply.Ok).result))
        assertEquals("on", PowerState.On.wire)
        assertEquals("standby", PowerState.Standby.wire)
        assertEquals("unknown", PowerState.Unknown.wire)
    }

    @Test fun parse_emptyResultOk() {
        val r = BraviaRpc.parseReply(200, """{"result":[],"id":55}""")
        assertTrue(r is Reply.Ok)
    }

    @Test fun parse_errors() {
        assertEquals(Reply.WrongKey, BraviaRpc.parseReply(200, """{"error":[40005,"Display Is Turned off"]}"""))
        assertEquals(Reply.WrongKey, BraviaRpc.parseReply(401, null))
        assertEquals(Reply.WrongKey, BraviaRpc.parseReply(403, "Forbidden"))
        assertEquals(Reply.Error(12, "Illegal Argument"), BraviaRpc.parseReply(200, """{"error":[12,"Illegal Argument"],"id":1}"""))
        assertTrue(BraviaRpc.parseReply(500, "oops") is Reply.Error)
        assertTrue(BraviaRpc.parseReply(200, "not json") is Reply.Error)
        assertTrue(BraviaRpc.parseReply(200, """{"id":1}""") is Reply.Error)
        assertTrue(BraviaRpc.parseReply(404, """{"result":[]}""") is Reply.Error)
    }

    @Test fun mac_fromSystemInformation() {
        val r = BraviaRpc.parseReply(200, """{"result":[{"product":"TV","macAddr":"AC:9B:0A:12:34:5F"}],"id":33}""")
        assertEquals("ac:9b:0a:12:34:5f", BraviaRpc.macOf((r as Reply.Ok).result))
        val none = BraviaRpc.parseReply(200, """{"result":[{"product":"TV"}]}""")
        assertNull(BraviaRpc.macOf((none as Reply.Ok).result))
    }

    @Test fun mac_parse() {
        assertEquals("ac:9b:0a:12:34:5f", BraviaRpc.normalizeMac("ac-9b-0a-12-34-5f"))
        assertEquals("ac:9b:0a:12:34:5f", BraviaRpc.normalizeMac("AC9B0A12345F"))
        assertNull(BraviaRpc.normalizeMac("ac:9b:0a:12:34"))
        assertNull(BraviaRpc.normalizeMac("zz:9b:0a:12:34:5f"))
        assertNull(BraviaRpc.normalizeMac(""))
    }

    @Test fun magicPacket() {
        val p = BraviaRpc.magicPacket("01:23:45:67:89:ab")!!
        assertEquals(102, p.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), p[i])
        val mac = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xab.toByte())
        for (r in 0 until 16) for (i in 0 until 6) assertEquals(mac[i], p[6 + r * 6 + i])
        assertNull(BraviaRpc.magicPacket("nope"))
    }

    @Test fun ip_helpers() {
        assertTrue(BraviaRpc.isValidIp("192.168.2.32"))
        assertTrue(BraviaRpc.isValidIp(" 10.0.0.1 "))
        assertFalse(BraviaRpc.isValidIp("192.168.2.256"))
        assertFalse(BraviaRpc.isValidIp("192.168.2"))
        assertFalse(BraviaRpc.isValidIp("tv.local"))
        assertFalse(BraviaRpc.isValidIp(""))
        assertEquals("192.168.2.255", BraviaRpc.subnetBroadcast("192.168.2.32"))
        assertNull(BraviaRpc.subnetBroadcast("bad"))
    }
}
