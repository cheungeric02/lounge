package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.abs

/** A phone → TV command (SPEC "v2 → Data contract", `cmd/<pushId>`). Pure. */
sealed interface RemoteCommand {
    /** [profile] (v4): name to auto-pick on the app's "Who's watching?" screen, e.g. YouTube. */
    data class Launch(val pkg: String, val profile: String? = null) : RemoteCommand
    data class Favorite(val pkg: String) : RemoteCommand
    data class Unfavorite(val pkg: String) : RemoteCommand
    data class Hide(val pkg: String) : RemoteCommand
    data class Unhide(val pkg: String) : RemoteCommand
    data class MoveFavorite(val pkg: String, val to: Int) : RemoteCommand
    data object Home : RemoteCommand
    data object Back : RemoteCommand

    // ---- v3 ----
    enum class MediaAction { Toggle, Play, Pause, Next, Prev, Seek }
    enum class VolumeAction { Up, Down, Mute }

    /** [ms] is the relative seek delta (only for [MediaAction.Seek]; null otherwise). */
    data class Media(val action: MediaAction, val ms: Long? = null) : RemoteCommand
    data class Volume(val action: VolumeAction) : RemoteCommand
    /** 0 cancels; 1..240 arms. */
    data class Sleep(val minutes: Int) : RemoteCommand
    /** Already validated by [LinkRouter.isAcceptable]. */
    data class Open(val url: String) : RemoteCommand
    /** Whole field value (≤ [TvInput.MAX_TEXT] chars); "" clears. */
    data class Text(val text: String) : RemoteCommand
    data object Submit : RemoteCommand

    companion object {
        /** Commands older/newer than this (vs estimated server time) are dropped. */
        const val FRESH_WINDOW_MS = 30_000L

        private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun isValidPkg(pkg: String?): Boolean = pkg != null && pkg.length <= 255 && PKG.matches(pkg)

        /** Parses `{type, pkg?, to?}` (ignores `at`); null for unknown type / bad or missing fields. */
        fun parse(element: JsonElement?): RemoteCommand? {
            val obj = element as? JsonObject ?: return null
            val type = obj.stringField("type") ?: return null
            val pkg = obj.stringField("pkg")?.takeIf(::isValidPkg)
            return when (type) {
                "launch" -> pkg?.let { Launch(it, ProfilePicker.normalizeProfile(obj.stringField("profile"))) }
                "favorite" -> pkg?.let(::Favorite)
                "unfavorite" -> pkg?.let(::Unfavorite)
                "hide" -> pkg?.let(::Hide)
                "unhide" -> pkg?.let(::Unhide)
                "moveFavorite" -> {
                    val to = obj.intField("to")
                    if (pkg != null && to != null) MoveFavorite(pkg, to) else null
                }
                "home" -> Home
                "back" -> Back
                "media" -> parseMedia(obj)
                "volume" -> when (obj.stringField("action")) {
                    "up" -> Volume(VolumeAction.Up)
                    "down" -> Volume(VolumeAction.Down)
                    "mute" -> Volume(VolumeAction.Mute)
                    else -> null
                }
                "sleep" -> {
                    val m = obj.longField("minutes")
                    if (m != null && m in 0..SleepTimer.MAX_MINUTES.toLong()) Sleep(m.toInt()) else null
                }
                "open" -> obj.stringField("url")?.trim()?.takeIf(LinkRouter::isAcceptable)?.let(::Open)
                "text" -> obj.stringField("text")?.takeIf { it.length <= TvInput.MAX_TEXT }?.let(::Text)
                "submit" -> Submit
                else -> null
            }
        }

        /** Largest |seek delta| accepted (24 h). */
        const val MAX_SEEK_MS = 86_400_000L

        private fun parseMedia(obj: JsonObject): Media? {
            val action = when (obj.stringField("action")) {
                "toggle" -> MediaAction.Toggle
                "play" -> MediaAction.Play
                "pause" -> MediaAction.Pause
                "next" -> MediaAction.Next
                "prev" -> MediaAction.Prev
                "seek" -> MediaAction.Seek
                else -> return null
            }
            if (action != MediaAction.Seek) return Media(action)
            val ms = obj.longField("ms") ?: return null
            if (ms == 0L || ms < -MAX_SEEK_MS || ms > MAX_SEEK_MS) return null
            return Media(action, ms)
        }

        /** The numeric server timestamp `at` (ms), or null if missing / non-numeric. */
        fun atOf(element: JsonElement?): Long? {
            val p = (element as? JsonObject)?.get("at") as? JsonPrimitive ?: return null
            if (p.isString || p is JsonNull) return null
            p.longOrNull?.let { return it }
            val d = p.doubleOrNull ?: return null
            return if (d.isFinite()) d.toLong() else null
        }

        fun isFresh(at: Long, serverNow: Long): Boolean = abs(serverNow - at) <= FRESH_WINDOW_MS

        /** Parse + freshness in one step: null unless valid AND fresh. */
        fun parseFresh(element: JsonElement?, serverNow: Long): RemoteCommand? {
            val at = atOf(element) ?: return null
            if (!isFresh(at, serverNow)) return null
            return parse(element)
        }

        /** RTDB keys can't contain '.', so `com.netflix.ninja` → `com,netflix,ninja`. */
        fun pkgKey(pkg: String): String = pkg.replace('.', ',')

        fun pkgFromKey(key: String): String = key.replace(',', '.')

        /** A single RTDB child key we are willing to address (push ids etc.). */
        fun isSafeChildKey(id: String): Boolean =
            id.isNotEmpty() && id.length <= 768 && id.none { it in ".#$[]/" || it.isISOControl() }

        /**
         * Command children carried by one stream event on `lounge/<key>/cmd`:
         * put/patch at "/" → every child of the object; put/patch at "/<id>" → that child (put with
         * null data = a deletion, ignored). Deeper paths (partial writes) are ignored.
         */
        fun childrenOf(event: SseEvent): List<Pair<String, JsonElement>> {
            val (path, json) = when (event) {
                is SseEvent.Put -> event.path to event.json
                is SseEvent.Patch -> event.path to event.json
                else -> return emptyList()
            }
            val data = try {
                Json.parseToJsonElement(json)
            } catch (_: Exception) {
                return emptyList()
            }
            val segs = path.split('/').filter { it.isNotEmpty() }
            return when (segs.size) {
                0 -> (data as? JsonObject)?.entries
                    ?.filter { (k, v) -> v !is JsonNull && isSafeChildKey(k) }
                    ?.map { (k, v) -> k to v }
                    ?: emptyList()
                1 -> if (data is JsonNull || !isSafeChildKey(segs[0])) emptyList() else listOf(segs[0] to data)
                else -> emptyList()
            }
        }

        private fun JsonObject.stringField(name: String): String? =
            (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.intField(name: String): Int? {
            val l = longField(name) ?: return null
            return l.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        }

        /** Integral number (JSON number, not string/bool); null otherwise. */
        private fun JsonObject.longField(name: String): Long? {
            val p = get(name) as? JsonPrimitive ?: return null
            if (p.isString || p is JsonNull) return null
            return p.longOrNull ?: p.doubleOrNull?.takeIf { it.isFinite() && it == Math.floor(it) && Math.abs(it) < 9.0e15 }?.toLong()
        }
    }
}
