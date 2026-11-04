package ca.cheungeric.lounge.ui.home

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "8:42" + "pm" + "Wednesday, November 4". */
data class ClockText(val time: String, val suffix: String, val date: String)

internal fun formatClock(now: LocalDateTime, locale: Locale): ClockText {
    val time = DateTimeFormatter.ofPattern("h:mm", locale).format(now)
    val suffix = DateTimeFormatter.ofPattern("a", locale).format(now).lowercase(locale).replace(".", "")
    val date = DateTimeFormatter.ofPattern("EEEE, MMMM d", locale).format(now)
    return ClockText(time, suffix, date)
}

/** Milliseconds from [now] until the next whole minute (always in 1..60_000). */
internal fun millisUntilNextMinute(now: LocalDateTime): Long {
    val intoMinute = now.second * 1000L + now.nano / 1_000_000L
    return (60_000L - intoMinute).coerceIn(1L, 60_000L)
}
