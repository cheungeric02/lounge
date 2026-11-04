package ca.cheungeric.lounge.update

import ca.cheungeric.lounge.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
)

private val versionJson = Json { ignoreUnknownKeys = true }

/** Parses version.json; null on any malformed input. */
fun parseVersionInfo(text: String): VersionInfo? =
    try {
        versionJson.decodeFromString(VersionInfo.serializer(), text)
    } catch (e: Exception) {
        null
    }

/** True when the published build is strictly newer than the installed one. */
fun isNewer(remote: Int, local: Int): Boolean = remote > local

/** Fetches version.json from GitHub Pages. */
class UpdateChecker(
    private val versionUrl: String = DEFAULT_VERSION_URL,
    private val localVersionCode: Int = BuildConfig.VERSION_CODE,
) {
    /** The published [VersionInfo] if it is newer than this build; null if up to date or on any error. */
    suspend fun check(): VersionInfo? = fetch().getOrNull()?.takeIf { isNewer(it.versionCode, localVersionCode) }

    /** Raw fetch + parse, keeping the error so a manual check can tell "up to date" from "offline". */
    internal suspend fun fetch(): Result<VersionInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("$versionUrl?t=${System.currentTimeMillis()}") // Pages caches ~10 min
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.setRequestProperty("Accept", "application/json")
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseVersionInfo(body) ?: error("Malformed version.json")
            } finally {
                conn.disconnect()
            }
        }
    }

    /** True if [info] is newer than this build. */
    fun isUpdate(info: VersionInfo): Boolean = isNewer(info.versionCode, localVersionCode)

    companion object {
        const val DEFAULT_VERSION_URL = "https://cheungeric02.github.io/lounge/version.json"
        private const val TIMEOUT_MS = 10_000
    }
}
