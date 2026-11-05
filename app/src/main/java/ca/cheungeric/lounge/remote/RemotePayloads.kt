package ca.cheungeric.lounge.remote

import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.HomeLayout
import ca.cheungeric.lounge.data.LayoutPrefsState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.Base64

/** Pure builders for the `lounge/<key>/tv/…` payloads (SPEC "v2 → Data contract"). */
object RemotePayloads {

    /** `tv/status`; lastSeen is the RTDB server-timestamp placeholder. */
    fun status(appVersion: Int, homeService: Boolean): String = buildJsonObject {
        put("appVersion", appVersion)
        put("homeService", homeService)
        put("lastSeen", buildJsonObject { put(".sv", "timestamp") })
    }.toString()

    /** `tv/apps`: pkgKey → {pkg, label, iconHash} for every installed app (incl. hidden). "" = no icon. */
    fun apps(installed: List<AppEntry>, iconHashes: Map<String, String>): String = JsonObject(
        installed.distinctBy { it.packageName }.associate { app ->
            RemoteCommand.pkgKey(app.packageName) to buildJsonObject {
                put("pkg", app.packageName)
                put("label", app.label)
                put("iconHash", iconHashes[app.packageName] ?: "")
            }
        },
    ).toString()

    /**
     * `tv/layout` as the TV shows it. Uninitialized prefs get the first-run defaults applied (what
     * Home will show once it seeds them). Hidden = installed hidden apps, alphabetical by label.
     */
    fun layout(installed: List<AppEntry>, prefs: LayoutPrefsState): String {
        val effective = HomeLayout.initDefaults(prefs, installed.map { it.packageName })
        val home = HomeLayout.build(installed, effective)
        val hidden = installed.distinctBy { it.packageName }
            .filter { it.packageName in effective.hidden }
            .sortedWith(compareBy<AppEntry>({ it.label.lowercase() }, { it.packageName }))
        fun arr(list: List<AppEntry>) = JsonArray(list.map { JsonPrimitive(it.packageName) })
        return JsonObject(
            mapOf(
                "favorites" to arr(home.favorites),
                "others" to arr(home.others),
                "hidden" to arr(hidden),
            ),
        ).toString()
    }

    fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    fun pngDataUri(png: ByteArray): String = "data:image/png;base64," + Base64.getEncoder().encodeToString(png)

    /** JSON string literal for a PUT of a bare string value. */
    fun jsonString(s: String): String = JsonPrimitive(s).toString()
}
