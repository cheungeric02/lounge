package ca.cheungeric.lounge.remote

/** One event from a Firebase REST stream. `data` of put/patch is the raw JSON `{"path":..,"data":..}`. */
sealed interface SseEvent {
    /** [path] is relative to the streamed location ("/" = the whole node); [json] is the raw `data` value. */
    data class Put(val path: String, val json: String) : SseEvent
    data class Patch(val path: String, val json: String) : SseEvent
    data object KeepAlive : SseEvent
    data object Cancel : SseEvent
    data object AuthRevoked : SseEvent
}

/**
 * Pure, incremental parser for Firebase RTDB REST streaming (text/event-stream). Feed arbitrary
 * chunks; complete events are returned as soon as their terminating blank line arrives. Handles
 * LF, CRLF and lone CR line endings, lines split across chunks, multi-line `data:`, comments.
 * Malformed put/patch payloads are dropped (never throws).
 */
class SseParser {
    private val line = StringBuilder()
    private var pendingCr = false
    private var eventName: String? = null
    private val data = StringBuilder()
    private var hasData = false

    fun feed(chunk: String): List<SseEvent> {
        val out = ArrayList<SseEvent>(1)
        for (c in chunk) {
            if (pendingCr) {
                pendingCr = false
                if (c == '\n') continue // CRLF: the CR already ended the line
            }
            when (c) {
                '\r' -> { pendingCr = true; endLine(out) }
                '\n' -> endLine(out)
                else -> line.append(c)
            }
        }
        return out
    }

    private fun endLine(out: MutableList<SseEvent>) {
        val l = line.toString()
        line.setLength(0)
        if (l.isEmpty()) {
            dispatch(out)
            return
        }
        if (l.startsWith(':')) return // comment
        val colon = l.indexOf(':')
        val field = if (colon < 0) l else l.substring(0, colon)
        var value = if (colon < 0) "" else l.substring(colon + 1)
        if (value.startsWith(' ')) value = value.substring(1)
        when (field) {
            "event" -> eventName = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
    }

    private fun dispatch(out: MutableList<SseEvent>) {
        val name = eventName
        val payload = data.toString()
        eventName = null
        data.setLength(0)
        hasData = false
        if (name == null) return
        val ev = when (name) {
            "put", "patch" -> parsePayload(name, payload)
            "keep-alive" -> SseEvent.KeepAlive
            "cancel" -> SseEvent.Cancel
            "auth_revoked" -> SseEvent.AuthRevoked
            else -> null
        }
        if (ev != null) out += ev
    }

    private fun parsePayload(name: String, payload: String): SseEvent? = try {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(payload) as? kotlinx.serialization.json.JsonObject
        val path = (obj?.get("path") as? kotlinx.serialization.json.JsonPrimitive)
            ?.takeIf { it.isString }?.content
        val d = obj?.get("data")
        if (path == null || d == null) {
            null
        } else if (name == "put") {
            SseEvent.Put(path, d.toString())
        } else {
            SseEvent.Patch(path, d.toString())
        }
    } catch (_: Exception) {
        null
    }
}
