package ca.cheungeric.lounge.remote

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure Now Playing logic (SPEC "v3 → tv/nowPlaying"): session selection, position extrapolation,
 * seek clamping and the payload builder. Android-free so it is unit-testable; MediaWatcher fills
 * [Session] from MediaController.
 */
object NowPlaying {

    // android.media.session.PlaybackState values (copied so this file stays Android-free).
    const val STATE_PAUSED = 2
    const val STATE_PLAYING = 3
    const val ACTION_SKIP_TO_PREVIOUS = 16L
    const val ACTION_SKIP_TO_NEXT = 32L
    const val ACTION_SEEK_TO = 256L

    /** One active media session, sampled from MediaController. Times are elapsedRealtime ms. */
    data class Session(
        val pkg: String,
        val app: String,
        val state: Int,
        val title: String = "",
        val subtitle: String = "",
        val durationMs: Long = 0,
        /** PlaybackState.position (-1 = unknown). */
        val positionMs: Long = 0,
        /** PlaybackState.lastPositionUpdateTime (elapsedRealtime ms). */
        val lastPositionUpdateMs: Long = 0,
        val speed: Float = 1f,
        val actions: Long = 0,
    ) {
        val isPlaying: Boolean get() = state == STATE_PLAYING
        val isPaused: Boolean get() = state == STATE_PAUSED
        val canSeek: Boolean get() = (actions and ACTION_SEEK_TO) != 0L && durationMs > 0
        val canSkip: Boolean get() = (actions and (ACTION_SKIP_TO_NEXT or ACTION_SKIP_TO_PREVIOUS)) != 0L
    }

    /**
     * First playing session (in the system's order, most recently active first), else the paused
     * session with the most recent position update, else null (`state:"none"`).
     */
    fun choose(sessions: List<Session>, elapsedNow: Long? = null): Session? =
        sessions.firstOrNull { it.isPlaying }
            ?: sessions.filter { it.isPaused && !isStalePaused(it, elapsedNow) }.maxByOrNull { it.lastPositionUpdateMs }

    /** A session paused for longer than this is forgotten (e.g. Spotify left paused after you listened elsewhere). */
    const val PAUSED_TTL_MS = 10 * 60_000L

    fun isStalePaused(s: Session, elapsedNow: Long?): Boolean =
        elapsedNow != null && s.lastPositionUpdateMs > 0 && elapsedNow - s.lastPositionUpdateMs > PAUSED_TTL_MS

    /**
     * Position at [elapsedNow]: while playing, `position + (elapsedNow - lastUpdate) * speed`;
     * otherwise the sampled position. Clamped to [0, duration] (upper bound only if duration > 0).
     */
    fun positionAt(s: Session, elapsedNow: Long): Long {
        val base = if (s.positionMs < 0) 0L else s.positionMs
        val pos = if (s.isPlaying && s.lastPositionUpdateMs > 0) {
            val dt = (elapsedNow - s.lastPositionUpdateMs).coerceAtLeast(0)
            base + (dt * s.speed.toDouble()).toLong()
        } else {
            base
        }
        return clampPos(pos, s.durationMs)
    }

    /** Position jumps smaller than this vs. the extrapolated position are not worth a rewrite. */
    const val DRIFT_TOLERANCE_MS = 1_500L

    /**
     * Whether `tv/nowPlaying` must be rewritten going from [prev] to [next]: any change except a
     * position update that matches the extrapolation (apps that re-post state every second).
     */
    fun needsRepublish(prev: Session?, next: Session?): Boolean {
        if (prev == null || next == null) return prev != next
        if (prev.copy(positionMs = 0, lastPositionUpdateMs = 0) != next.copy(positionMs = 0, lastPositionUpdateMs = 0)) {
            return true
        }
        val expected = positionAt(prev, next.lastPositionUpdateMs)
        val actual = positionAt(next, next.lastPositionUpdateMs)
        return kotlin.math.abs(expected - actual) > DRIFT_TOLERANCE_MS
    }

    /** Relative seek target: current + [deltaMs], clamped to [0, duration] (or ≥ 0 if no duration). */
    fun seekTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val sum = if (deltaMs > 0 && currentMs > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE else currentMs + deltaMs
        return clampPos(sum, durationMs)
    }

    private fun clampPos(pos: Long, durationMs: Long): Long {
        val lo = pos.coerceAtLeast(0)
        return if (durationMs > 0) lo.coerceAtMost(durationMs) else lo
    }

    /**
     * `tv/nowPlaying`. [positionAtServer] = estimated server time (ms) the position is sampled at;
     * the position is extrapolated to [elapsedNow]. [artHash] "" = no art.
     */
    fun payload(s: Session?, artHash: String, elapsedNow: Long, positionAtServer: Long): String = buildJsonObject {
        if (s == null) {
            put("state", "none")
            put("pkg", "")
            put("app", "")
            put("title", "")
            put("subtitle", "")
            put("durationMs", 0)
            put("positionMs", 0)
            put("positionAt", positionAtServer)
            put("speed", 0)
            put("artHash", "")
            put("canSeek", false)
            put("canSkip", false)
        } else {
            put("state", if (s.isPlaying) "playing" else "paused")
            put("pkg", s.pkg)
            put("app", s.app)
            put("title", s.title)
            put("subtitle", s.subtitle)
            put("durationMs", s.durationMs.coerceAtLeast(0))
            put("positionMs", positionAt(s, elapsedNow))
            put("positionAt", positionAtServer)
            put("speed", if (s.speed.isFinite()) s.speed.toDouble() else 0.0)
            put("artHash", artHash)
            put("canSeek", s.canSeek)
            put("canSkip", s.canSkip)
        }
    }.toString()

    /** Size that fits ([w], [h]) inside [max]×[max] keeping aspect (never upscales, min 1 px). */
    fun fitWithin(w: Int, h: Int, max: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 1 to 1
        if (w <= max && h <= max) return w to h
        return if (w >= h) {
            max to (h.toLong() * max / w).toInt().coerceAtLeast(1)
        } else {
            (w.toLong() * max / h).toInt().coerceAtLeast(1) to max
        }
    }
}
