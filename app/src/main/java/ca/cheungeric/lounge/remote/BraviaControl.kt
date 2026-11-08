package ca.cheungeric.lounge.remote

import android.content.Context
import android.util.Log
import ca.cheungeric.lounge.remote.BraviaRpc.PowerState
import ca.cheungeric.lounge.remote.BraviaRpc.Reply
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Sony Bravia IP control over the LAN (SPEC "v5 → Bravia IP control"). Process-wide (AppContainer).
 * Config (IP, Pre-Shared Key, cached MAC) lives in `lounge_prefs`. All IO runs on Dispatchers.IO and
 * never throws to callers. The PSK is only ever sent in the X-Auth-PSK header to the configured
 * private IPv4 address — never logged, never published.
 */
class BraviaControl(context: Context) {

    sealed interface Result {
        /** [state] is set for power-status calls, null for commands without a state answer. */
        data class Ok(val state: PowerState? = null) : Result
        data object WrongKey : Result
        data object Unreachable : Result
        data object NotConfigured : Result
        data class Error(val message: String) : Result
    }

    private val app = context.applicationContext
    private val prefs get() = app.getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE)

    /** True after the last call succeeded (any Ok); false after a failure or config change. */
    @Volatile
    var lastOk: Boolean = false
        private set

    val ip: String get() = read(KEY_IP)?.takeIf { it.isNotBlank() } ?: DEFAULT_IP
    val psk: String get() = read(KEY_PSK) ?: ""
    private val mac: String? get() = read(KEY_MAC)?.let(BraviaRpc::normalizeMac)

    /** HDMI port (1–4) the TV box is plugged into; phone Home switches the TV to it. */
    val boxPort: Int get() = try {
        prefs.getInt(KEY_BOX_PORT, DEFAULT_BOX_PORT).takeIf { it in 1..4 } ?: DEFAULT_BOX_PORT
    } catch (_: Throwable) {
        DEFAULT_BOX_PORT
    }

    fun saveBoxPort(port: Int) {
        if (port !in 1..4) return
        try {
            prefs.edit().putInt(KEY_BOX_PORT, port).apply()
        } catch (t: Throwable) {
            log("saveBoxPort", t)
        }
    }

    val isConfigured: Boolean get() = psk.isNotBlank() && BraviaRpc.isValidIp(ip)

    /** Saves IP + PSK (trimmed). A changed IP or key forgets the cached MAC and the last result. */
    fun save(ip: String, psk: String) {
        val newIp = ip.trim().ifEmpty { DEFAULT_IP }
        val newPsk = psk.trim()
        val changed = newIp != this.ip || newPsk != this.psk
        try {
            prefs.edit().apply {
                putString(KEY_IP, newIp)
                putString(KEY_PSK, newPsk)
                if (changed) remove(KEY_MAC)
            }.apply()
        } catch (t: Throwable) {
            log("save", t)
        }
        if (changed) lastOk = false
    }

    suspend fun powerStatus(): Result {
        val r = call(BraviaRpc.SERVICE_SYSTEM, BraviaRpc.getPowerStatus())
        return if (r is Reply.Ok) Result.Ok(BraviaRpc.powerStateOf(r.result)).also { afterOk() } else map(r)
    }

    /** Settings → "Test connection". */
    suspend fun test(): Result = powerStatus()

    /** `on` sends Wake-on-LAN first when the MAC is known (a deep-standby TV may not answer HTTP). */
    suspend fun setPower(on: Boolean): Result {
        if (!isConfigured) return Result.NotConfigured
        val woke = on && mac?.let { sendWol(it) } == true
        var r = call(BraviaRpc.SERVICE_SYSTEM, BraviaRpc.setPowerStatus(on))
        if (woke && r == null) {
            delay(WOL_RETRY_DELAY_MS)
            r = call(BraviaRpc.SERVICE_SYSTEM, BraviaRpc.setPowerStatus(on))
        }
        return if (r is Reply.Ok) Result.Ok().also { afterOk() } else map(r)
    }

    /** Reads the power state first, then flips it (unknown → on). */
    suspend fun toggle(): Result {
        return when (val s = powerStatus()) {
            is Result.Ok -> setPower(s.state != PowerState.On)
            // Unreachable may mean deep standby: try waking it.
            Result.Unreachable -> if (mac != null) setPower(true) else s
            else -> s
        }
    }

    suspend fun setInput(port: Int): Result {
        if (port !in 1..4) return Result.Error("bad port")
        val r = call(BraviaRpc.SERVICE_AV_CONTENT, BraviaRpc.setPlayContent(port))
        return if (r is Reply.Ok) Result.Ok().also { afterOk() } else map(r)
    }

    /** Outcome of [inputs]. */
    sealed interface InputsResult {
        /** [activeUri] is what the TV shows now; null when unknown or not an input. */
        data class Ok(val inputs: List<BraviaRpc.HdmiInput>, val activeUri: String?) : InputsResult
        data object NotConfigured : InputsResult
        data object WrongKey : InputsResult
        data object Unreachable : InputsResult
        data class Error(val message: String) : InputsResult
    }

    /** HDMI inputs (v1.1, falling back to 1.0) plus the playing uri (a failure there → null). */
    suspend fun inputs(): InputsResult {
        if (!isConfigured) return InputsResult.NotConfigured
        var r = call(BraviaRpc.SERVICE_AV_CONTENT, BraviaRpc.getCurrentExternalInputsStatus("1.1"))
        if (r is Reply.Error) r = call(BraviaRpc.SERVICE_AV_CONTENT, BraviaRpc.getCurrentExternalInputsStatus("1.0"))
        if (r !is Reply.Ok) {
            return when (val m = map(r)) {
                Result.WrongKey -> InputsResult.WrongKey
                Result.Unreachable -> InputsResult.Unreachable
                Result.NotConfigured -> InputsResult.NotConfigured
                is Result.Error -> InputsResult.Error(m.message)
                is Result.Ok -> InputsResult.Error("error")
            }
        }
        val list = BraviaRpc.hdmiInputsOf(r.result)
        val playing = call(BraviaRpc.SERVICE_AV_CONTENT, BraviaRpc.getPlayingContentInfo())
        val active = (playing as? Reply.Ok)?.let { BraviaRpc.playingUriOf(it.result) }
        afterOk()
        return InputsResult.Ok(list, active)
    }

    /** Switches to the HDMI input [uri] (must start with `extInput:hdmi`). */
    suspend fun setInputUri(uri: String): Result {
        if (!uri.startsWith(BraviaRpc.HDMI_PREFIX)) return Result.Error("bad input")
        val r = call(BraviaRpc.SERVICE_AV_CONTENT, BraviaRpc.setPlayContent(uri))
        return if (r is Reply.Ok) Result.Ok().also { afterOk() } else map(r)
    }

    private fun map(r: Reply?): Result {
        lastOk = false
        return when (r) {
            null -> if (isConfigured) Result.Unreachable else Result.NotConfigured
            Reply.WrongKey -> Result.WrongKey
            is Reply.Error -> Result.Error(if (r.message.isNotBlank()) r.message else "error ${r.code}")
            is Reply.Ok -> Result.Ok()
        }
    }

    /** Marks success and, on the first success, learns + caches the TV's MAC for Wake-on-LAN. */
    private suspend fun afterOk() {
        lastOk = true
        if (mac != null) return
        val r = call(BraviaRpc.SERVICE_SYSTEM, BraviaRpc.getSystemInformation())
        val m = (r as? Reply.Ok)?.let { BraviaRpc.macOf(it.result) } ?: return
        try {
            prefs.edit().putString(KEY_MAC, m).apply()
        } catch (t: Throwable) {
            log("save mac", t)
        }
    }

    /** One JSON-RPC POST; null = not configured or unreachable (timeout / no route / IO error). */
    private suspend fun call(service: String, body: String): Reply? = withContext(Dispatchers.IO) {
        val host = ip
        val key = psk
        if (key.isBlank() || !BraviaRpc.isValidIp(host)) return@withContext null
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("http://${host.trim()}/sony/$service").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("X-Auth-PSK", key)
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { s -> s.readBytes().takeIf { it.size <= MAX_BODY }?.toString(Charsets.UTF_8) }
            BraviaRpc.parseReply(code, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log("call $service", e)
            null
        } catch (t: Throwable) {
            log("call $service", t)
            Reply.Error(-1, t.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }

    /** Magic packet to 255.255.255.255:9 and the /24 broadcast of the TV's IP. True if any send worked. */
    private suspend fun sendWol(mac: String): Boolean = withContext(Dispatchers.IO) {
        val packet = BraviaRpc.magicPacket(mac) ?: return@withContext false
        val targets = listOfNotNull("255.255.255.255", BraviaRpc.subnetBroadcast(ip)).distinct()
        var sent = false
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                repeat(WOL_REPEATS) {
                    for (t in targets) {
                        try {
                            sock.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), WOL_PORT))
                            sent = true
                        } catch (e: IOException) {
                            log("wol $t", e)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            log("wol", t)
        }
        sent
    }

    private fun read(key: String): String? = try {
        prefs.getString(key, null)
    } catch (_: Throwable) {
        null
    }

    private fun log(what: String, t: Throwable) {
        // Class name only: never risk echoing request details (the PSK header).
        Log.w(TAG, "$what: ${t.javaClass.simpleName}")
    }

    companion object {
        private const val TAG = "LoungeBravia"
        const val DEFAULT_IP = "192.168.2.32"
        const val KEY_IP = "lounge_braviaIp"
        const val KEY_PSK = "lounge_braviaPsk"
        const val KEY_MAC = "lounge_braviaMac"
        const val KEY_BOX_PORT = "lounge_braviaBoxPort"
        const val DEFAULT_BOX_PORT = 2
        private const val TIMEOUT_MS = 3_000
        private const val MAX_BODY = 64 * 1024
        private const val WOL_PORT = 9
        private const val WOL_REPEATS = 3
        private const val WOL_RETRY_DELAY_MS = 2_000L
    }
}
