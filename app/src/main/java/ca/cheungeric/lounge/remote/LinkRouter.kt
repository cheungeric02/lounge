package ca.cheungeric.lounge.remote

import java.net.URI
import java.net.URISyntaxException

/**
 * Pure URL → TV app routing for the `open` command (SPEC "v3 → Data contract additions").
 * Only http/https, ≤ [MAX_LEN] chars. Uses java.net.URI (no android.net.Uri) so it is unit-testable.
 */
object LinkRouter {

    const val MAX_LEN = 2048

    const val YOUTUBE = "com.google.android.youtube.tv"
    const val NETFLIX = "com.netflix.ninja"
    const val SPOTIFY = "com.spotify.tv.android"
    const val PRIME = "com.amazon.amazonvideo.livingroom"
    const val DISNEY = "com.disney.disneyplus"

    /** Every package LinkRouter can target (manifest `<queries>` must list these). */
    val TARGET_PACKAGES = listOf(YOUTUBE, NETFLIX, SPOTIFY, PRIME, DISNEY)

    /**
     * Where to send a link. [pkg] null = plain ACTION_VIEW (any resolver). [targets] is the try order:
     * the mapped app first (if any), then plain ACTION_VIEW.
     */
    data class Route(val url: String, val pkg: String?) {
        val targets: List<String?> get() = if (pkg == null) listOf(null) else listOf(pkg, null)
    }

    private val YT_ID = Regex("^[A-Za-z0-9_-]{6,20}$")
    private val NETFLIX_PATH = Regex("^/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:title|watch)/\\d+(?:/.*)?$", RegexOption.IGNORE_CASE)
    private val AMAZON_HOST = Regex("^(?:www\\.)?amazon(?:\\.[a-z]{2,3}){1,2}$")

    /** Parsed http(s) URI, or null if the string is not an acceptable link. */
    fun parse(raw: String?): URI? {
        if (raw == null) return null
        val s = raw.trim()
        if (s.isEmpty() || s.length > MAX_LEN) return null
        if (s.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = try {
            URI(s)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.isOpaque) return null
        val host = uri.host ?: return null
        if (host.isEmpty()) return null
        return uri
    }

    fun isAcceptable(raw: String?): Boolean = parse(raw) != null

    /** The route for [raw], or null if it's rejected (non-http(s), too long, unparseable). */
    fun route(raw: String?): Route? {
        val uri = parse(raw) ?: return null
        val url = raw!!.trim()
        val host = uri.host.lowercase().trimEnd('.')
        val path = uri.rawPath ?: ""
        return when {
            host == "youtu.be" -> {
                val id = path.trim('/').substringBefore('/')
                if (YT_ID.matches(id)) Route(youtubeWatch(id, queryParam(uri, "t")), YOUTUBE) else Route(url, YOUTUBE)
            }
            isHost(host, "youtube.com") -> {
                val segs = path.split('/').filter { it.isNotEmpty() }
                if (segs.size >= 2 && segs[0] == "shorts" && YT_ID.matches(segs[1])) {
                    Route(youtubeWatch(segs[1], null), YOUTUBE)
                } else {
                    Route(url, YOUTUBE)
                }
            }
            isHost(host, "netflix.com") && NETFLIX_PATH.matches(path) -> Route(url, NETFLIX)
            host == "open.spotify.com" -> Route(url, SPOTIFY)
            isHost(host, "primevideo.com") -> Route(url, PRIME)
            AMAZON_HOST.matches(host) && (path == "/gp/video" || path.startsWith("/gp/video/")) -> Route(url, PRIME)
            isHost(host, "disneyplus.com") -> Route(url, DISNEY)
            else -> Route(url, null)
        }
    }

    private fun isHost(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun youtubeWatch(id: String, t: String?): String {
        val tt = t?.takeIf { Regex("^[0-9hms]{1,12}$").matches(it) }
        return "https://www.youtube.com/watch?v=$id" + (tt?.let { "&t=$it" } ?: "")
    }

    private fun queryParam(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')?.firstNotNullOfOrNull { kv ->
            val i = kv.indexOf('=')
            if (i > 0 && kv.substring(0, i) == name) kv.substring(i + 1) else null
        }
}
