package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Pure sleep-timer rules (SPEC "v3 → sleep"). Times are estimated server ms. */
object SleepTimer {

    const val MAX_MINUTES = 240

    /** Expiry time for [minutes] (1..240) from [serverNow]; null for 0 (= cancel). */
    fun atFor(minutes: Int, serverNow: Long): Long? =
        if (minutes <= 0) null else serverNow + minutes.coerceAtMost(MAX_MINUTES) * 60_000L

    /** What to do with a `tv/sleep` value found when the bridge (re)starts. */
    sealed interface Rearm {
        /** Nothing stored: nothing to do. */
        data object None : Rearm
        /** Still in the future: arm for [delayMs]. */
        data class Arm(val at: Long, val delayMs: Long) : Rearm
        /** Expired while the bridge wasn't running (or garbage): delete it, don't fire late. */
        data object Clear : Rearm
    }

    /**
     * Decision for the raw JSON GET of `tv/sleep` ("null" when absent). A timer more than
     * [MAX_MINUTES] in the future is treated as garbage (cleared).
     */
    fun rearm(json: String?, serverNow: Long): Rearm {
        if (json == null) return Rearm.None
        val el = try {
            Json.parseToJsonElement(json)
        } catch (_: Exception) {
            return Rearm.Clear
        }
        if (el is JsonNull) return Rearm.None
        val p = (el as? JsonObject)?.get("at") as? JsonPrimitive ?: return Rearm.Clear
        if (p.isString || p is JsonNull) return Rearm.Clear
        val at = p.longOrNull ?: p.doubleOrNull?.takeIf { it.isFinite() }?.toLong() ?: return Rearm.Clear
        val delay = at - serverNow
        return when {
            delay <= 0 -> Rearm.Clear
            delay > MAX_MINUTES * 60_000L + 60_000L -> Rearm.Clear
            else -> Rearm.Arm(at, delay)
        }
    }
}
