package ca.cheungeric.lounge.ui.ambient

/** Pure idle-overlay rules for the ambient screen on Lounge Home. */
object AmbientIdle {
    /** lounge_prefs key; value = idle minutes, 0 = Off. */
    const val PREF_KEY = "lounge_ambientMinutes"
    const val DEFAULT_MINUTES = 10

    /** Settings choices, in display order (0 = Off). */
    val OPTIONS: List<Int> = listOf(0, 5, 10, 30)

    /** Unknown stored values fall back to the default. */
    fun normalize(minutes: Int): Int = if (minutes in OPTIONS) minutes else DEFAULT_MINUTES

    sealed interface Decision {
        /** Setting is Off: never show. */
        data object Off : Decision
        /** Not on plain Home (Settings / dialog open / not resumed): restart the idle count. */
        data object Blocked : Decision
        /** Check again after [millis]. */
        data class Wait(val millis: Long) : Decision
        data object Show : Decision
    }

    /**
     * @param minutes idle timeout (0 = Off)
     * @param lastInputMs uptime of the last key press (or of the last reset)
     * @param onPlainHome Home is showing with no Settings / dialog / sheet open and the activity is resumed
     */
    fun decide(minutes: Int, lastInputMs: Long, nowMs: Long, onPlainHome: Boolean): Decision {
        if (minutes <= 0) return Decision.Off
        if (!onPlainHome) return Decision.Blocked
        val remaining = lastInputMs + minutes * 60_000L - nowMs
        return if (remaining > 0) Decision.Wait(remaining) else Decision.Show
    }
}
