package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Pure Sony Bravia REST (JSON-RPC over HTTP) request builders + response parsing, and the
 * Wake-on-LAN magic packet (SPEC "v5 → Bravia IP control"). No IO here; see [BraviaControl].
 */
object BraviaRpc {

    const val SERVICE_SYSTEM = "system"
    const val SERVICE_AV_CONTENT = "avContent"

    /** Sony's "authentication failed / forbidden" JSON-RPC error code. */
    const val ERROR_AUTH = 40005

    enum class PowerState(val wire: String) { On("on"), Standby("standby"), Unknown("unknown") }

    /** Outcome of one JSON-RPC call (transport errors are mapped by [BraviaControl]). */
    sealed interface Reply {
        /** [result] is the `result` array (may be empty). */
        data class Ok(val result: JsonArray) : Reply
        data object WrongKey : Reply
        data class Error(val code: Int, val message: String) : Reply
    }

    private fun request(method: String, id: Int, param: JsonObject?, version: String = "1.0"): String =
        buildJsonObject {
            put("method", method)
            put("id", id)
            putJsonArray("params") { if (param != null) add(param) }
            put("version", version)
        }.toString()

    fun getPowerStatus(): String = request("getPowerStatus", 50, null)

    fun setPowerStatus(on: Boolean): String =
        request("setPowerStatus", 55, buildJsonObject { put("status", on) })

    /** `port` 1..4 → `extInput:hdmi?port=N`. */
    fun setPlayContent(port: Int): String {
        require(port in 1..4) { "port out of range" }
        return setPlayContent("extInput:hdmi?port=$port")
    }

    /** Switches to an HDMI input by its full uri; only `extInput:hdmi…` uris are accepted. */
    fun setPlayContent(uri: String): String {
        require(uri.startsWith(HDMI_PREFIX)) { "not an HDMI uri" }
        return request("setPlayContent", 101, buildJsonObject { put("uri", uri) })
    }

    /** Lists external inputs with labels + connection state (avContent; v1.1, older sets 1.0). */
    fun getCurrentExternalInputsStatus(version: String = "1.1"): String =
        request("getCurrentExternalInputsStatus", 105, null, version)

    /** What the TV is showing now (avContent). */
    fun getPlayingContentInfo(): String = request("getPlayingContentInfo", 103, null)

    const val HDMI_PREFIX = "extInput:hdmi"

    /** One HDMI input from getCurrentExternalInputsStatus. */
    data class HdmiInput(
        val uri: String,
        val port: Int,
        val title: String,
        val label: String,
        val connected: Boolean,
    ) {
        /** The user's TV label, else Sony's title, else "HDMI N". */
        val displayName: String
            get() = label.trim().ifBlank { title.trim().ifBlank { "HDMI $port" } }
    }

    private val PORT = Regex("[?&]port=(\\d+)")

    /** HDMI entries of `result[0]` (array of {uri,title,label,connection,…}), sorted by port. */
    fun hdmiInputsOf(result: JsonArray): List<HdmiInput> {
        val items = result.firstOrNull() as? JsonArray ?: return emptyList()
        return items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val uri = o.str("uri") ?: return@mapNotNull null
            if (!uri.startsWith(HDMI_PREFIX)) return@mapNotNull null
            val port = PORT.find(uri)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val connected = (o["connection"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: false
            HdmiInput(uri, port, o.str("title") ?: "", o.str("label") ?: "", connected)
        }.sortedBy { it.port }
    }

    /** `result[0].uri` of getPlayingContentInfo, or null (e.g. TV apps / tuner). */
    fun playingUriOf(result: JsonArray): String? = firstObject(result)?.str("uri")?.takeIf { it.isNotBlank() }

    fun getSystemInformation(): String = request("getSystemInformation", 33, null)

    /** Maps an HTTP status + body to a [Reply]. 401/403 or error 40005 → [Reply.WrongKey]. */
    fun parseReply(httpCode: Int, body: String?): Reply {
        if (httpCode == 401 || httpCode == 403) return Reply.WrongKey
        val obj = try {
            body?.let { Json.parseToJsonElement(it) } as? JsonObject
        } catch (_: Exception) {
            null
        }
        if (obj == null) return Reply.Error(httpCode, "HTTP $httpCode")
        (obj["error"] as? JsonArray)?.let { err ->
            val code = (err.getOrNull(0) as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.toInt() ?: -1
            if (code == ERROR_AUTH) return Reply.WrongKey
            val msg = (err.getOrNull(1) as? JsonPrimitive)?.content ?: ""
            return Reply.Error(code, msg)
        }
        if (httpCode !in 200..299) return Reply.Error(httpCode, "HTTP $httpCode")
        val result = obj["result"] as? JsonArray ?: return Reply.Error(-1, "no result")
        return Reply.Ok(result)
    }

    /** `{"result":[{"status":"active"}]}` → On, "standby" → Standby, else Unknown. */
    fun powerStateOf(result: JsonArray): PowerState =
        when (firstObject(result)?.str("status")) {
            "active" -> PowerState.On
            "standby" -> PowerState.Standby
            else -> PowerState.Unknown
        }

    /** `macAddr` from getSystemInformation, normalized `aa:bb:cc:dd:ee:ff`; null if absent/invalid. */
    fun macOf(result: JsonArray): String? = firstObject(result)?.str("macAddr")?.let(::normalizeMac)

    /** Accepts `AA:BB:..`, `aa-bb-..` or 12 bare hex digits; returns lowercase colon form or null. */
    fun normalizeMac(mac: String): String? {
        val bytes = parseMac(mac) ?: return null
        return bytes.joinToString(":") { "%02x".format(it) }
    }

    fun parseMac(mac: String): ByteArray? {
        val hex = mac.trim().replace(":", "").replace("-", "")
        if (hex.length != 12 || hex.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    /** Wake-on-LAN magic packet: 6 × 0xFF then the MAC 16 times (102 bytes). Null for a bad MAC. */
    fun magicPacket(mac: String): ByteArray? {
        val m = parseMac(mac) ?: return null
        val out = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) out[i] = 0xFF.toByte()
        for (r in 0 until 16) m.copyInto(out, 6 + r * 6)
        return out
    }

    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** Dotted IPv4 with each octet 0..255. */
    fun isValidIp(ip: String): Boolean {
        val m = IPV4.matchEntire(ip.trim()) ?: return false
        return m.groupValues.drop(1).all { it.toInt() in 0..255 }
    }

    /** The /24 broadcast for [ip] (`192.168.2.32` → `192.168.2.255`), or null if not IPv4. */
    fun subnetBroadcast(ip: String): String? {
        if (!isValidIp(ip)) return null
        return ip.trim().substringBeforeLast('.') + ".255"
    }

    private fun firstObject(result: JsonArray): JsonObject? = result.firstOrNull() as? JsonObject

    private fun JsonObject.str(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
