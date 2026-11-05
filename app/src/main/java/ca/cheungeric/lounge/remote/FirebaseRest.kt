package ca.cheungeric.lounge.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The slice of the RTDB REST API the bridge uses; faked in tests (no live network in tests). */
interface RemoteDb {
    /** Raw JSON at [path] ("null" if absent). Throws IOException on failure. */
    suspend fun get(path: String): String
    suspend fun put(path: String, json: String)
    suspend fun patch(path: String, json: String)
    suspend fun delete(path: String)

    /**
     * Streams [path] until the server closes the stream, [onEvent] throws, or the caller is
     * cancelled (the connection is then closed). Throws IOException on connection failure.
     */
    suspend fun stream(path: String, onEvent: (SseEvent) -> Unit)

    /** Best estimate of the server's clock (ms since epoch). */
    fun serverNowMs(): Long
}

/**
 * Firebase RTDB REST client on HttpURLConnection (no extra dependencies). Every path must be
 * inside `lounge/<validKey>` — enforced by [urlFor] — so this client can never write anywhere else
 * on Eric's shared bon-echo database.
 */
class FirebaseRest(
    private val baseUrl: String = BASE_URL,
) : RemoteDb {

    @Volatile
    private var clockOffsetMs: Long = 0L

    override fun serverNowMs(): Long = System.currentTimeMillis() + clockOffsetMs

    override suspend fun get(path: String): String = request("GET", path, null)

    override suspend fun put(path: String, json: String) {
        request("PUT", path, json)
    }

    override suspend fun patch(path: String, json: String) {
        // HttpURLConnection has no PATCH; Firebase honours the method-override header on POST.
        request("PATCH", path, json)
    }

    override suspend fun delete(path: String) {
        request("DELETE", path, null)
    }

    private suspend fun request(method: String, path: String, body: String?): String =
        withContext(Dispatchers.IO) {
            val url = URL(urlFor(baseUrl, path))
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.useCaches = false
                if (method == "PATCH") {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    conn.requestMethod = method
                }
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                }
                val code = conn.responseCode
                noteDate(conn)
                if (code !in 200..299) {
                    runCatching { conn.errorStream?.close() }
                    throw IOException("HTTP $code for $method")
                }
                conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                conn.disconnect()
            }
        }

    override suspend fun stream(path: String, onEvent: (SseEvent) -> Unit) {
        var url = urlFor(baseUrl, path)
        var conn: HttpURLConnection? = null
        try {
            withContext(Dispatchers.IO) {
                var redirects = 0
                while (true) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    conn = c
                    c.instanceFollowRedirects = false
                    c.connectTimeout = CONNECT_TIMEOUT_MS
                    // Firebase sends keep-alive every ~30 s; a much longer silence means a dead link.
                    c.readTimeout = STREAM_READ_TIMEOUT_MS
                    c.useCaches = false
                    c.setRequestProperty("Accept", "text/event-stream")
                    val code = c.responseCode
                    noteDate(c)
                    if (code in 300..399) {
                        val loc = c.getHeaderField("Location")
                        c.disconnect()
                        if (loc == null || ++redirects > MAX_REDIRECTS) throw IOException("bad redirect")
                        url = checkRedirect(url, loc)
                        continue
                    }
                    if (code !in 200..299) throw IOException("stream HTTP $code")
                    break
                }
            }
            val c = conn ?: return
            coroutineScope {
                // Closing the connection is the only way to unblock a pending read.
                val closer = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        runCatching { c.disconnect() }
                    }
                }
                try {
                    runInterruptible(Dispatchers.IO) {
                        val parser = SseParser()
                        InputStreamReader(c.inputStream, Charsets.UTF_8).use { reader ->
                            val buf = CharArray(4096)
                            while (true) {
                                val n = reader.read(buf)
                                if (n < 0) break
                                for (ev in parser.feed(String(buf, 0, n))) onEvent(ev)
                            }
                        }
                    }
                } finally {
                    closer.cancel()
                }
            }
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun noteDate(conn: HttpURLConnection) {
        val date = conn.getHeaderFieldDate("Date", 0L)
        if (date > 0L) {
            // Date has 1 s resolution; +500 ms centres the truncation error.
            clockOffsetMs = date + 500 - System.currentTimeMillis()
        }
    }

    companion object {
        const val BASE_URL = "https://bon-echo-bd946-default-rtdb.firebaseio.com"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val STREAM_READ_TIMEOUT_MS = 75_000
        private const val MAX_REDIRECTS = 5

        /**
         * `<base>/<encoded segments>.json`. Requires `lounge/<validKey>` or a path below it, with no
         * empty, "." or ".." segments.
         */
        fun urlFor(base: String, path: String): String {
            val segs = path.split('/')
            require(segs.size >= 2 && segs[0] == "lounge" && RemoteKey.isValid(segs[1])) {
                "path outside lounge/<key>"
            }
            require(segs.none { it.isEmpty() || it == "." || it == ".." }) { "bad path segment" }
            val encoded = segs.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            return "${base.trimEnd('/')}/$encoded.json"
        }

        /** Streams redirect to another Firebase host; only accept https Firebase hosts, same path. */
        internal fun checkRedirect(from: String, location: String): String {
            val target = URL(URL(from), location)
            val host = target.host.lowercase()
            val okHost = host.endsWith(".firebaseio.com") || host.endsWith(".firebasedatabase.app")
            if (target.protocol != "https" || !okHost || target.path != URL(from).path) {
                throw IOException("refusing redirect")
            }
            return target.toString()
        }
    }
}
