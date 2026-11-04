package ca.cheungeric.lounge.update

import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
)

/** Fetches version.json from GitHub Pages. TODO(T6): implement with HttpURLConnection. */
class UpdateChecker(
    private val versionUrl: String = "https://cheungeric02.github.io/lounge/version.json",
) {
    suspend fun check(): VersionInfo? {
        // TODO(T6)
        return null
    }
}
