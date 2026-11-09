package ca.cheungeric.lounge.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ca.cheungeric.lounge.home.MediaAccessStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Watches active media sessions (needs Notification access for [ca.cheungeric.lounge.home.MediaAccessService])
 * and offers transport controls. Never throws: without access [reports] emits [Report.NO_ACCESS] and ends.
 */
class MediaWatcher(context: Context, private val labelOf: (String) -> String) {

    private val app = context.applicationContext

    /** One sample of every active session, in the system's order (most recently active first). */
    data class Report(val access: Boolean, val sessions: List<NowPlaying.Session>, val art: Map<String, Bitmap?>) {
        companion object {
            val NO_ACCESS = Report(false, emptyList(), emptyMap())
        }
    }

    /** Controllers from the latest bind; touched on the main thread only. */
    private var controllers: List<MediaController> = emptyList()

    /**
     * Emits a [Report] on every session list / metadata / playback-state change. Ends (after emitting
     * [Report.NO_ACCESS]) when access isn't granted or gets revoked; callers retry later.
     */
    fun reports(): Flow<Report> = callbackFlow {
        val msm = try {
            app.getSystemService(MediaSessionManager::class.java)
        } catch (t: Throwable) {
            null
        }
        if (msm == null) {
            trySend(Report.NO_ACCESS)
            close()
            return@callbackFlow
        }
        val component = MediaAccessStatus.component(app)
        val handler = Handler(Looper.getMainLooper())
        val bound = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

        fun emit() {
            trySend(sampleAll(controllers))
        }

        fun bind(list: List<MediaController>?) {
            val now = list.orEmpty()
            val tokens = now.mapTo(HashSet()) { it.sessionToken }
            for ((token, pair) in bound.entries.toList()) {
                if (token !in tokens) {
                    runCatching { pair.first.unregisterCallback(pair.second) }
                    bound.remove(token)
                }
            }
            for (c in now) {
                if (bound.containsKey(c.sessionToken)) continue
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
                    override fun onSessionDestroyed() = emit()
                }
                try {
                    c.registerCallback(cb, handler)
                    bound[c.sessionToken] = c to cb
                } catch (t: Throwable) {
                    log("registerCallback", t)
                }
            }
            controllers = now
            emit()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            try {
                bind(list)
            } catch (t: Throwable) {
                log("sessions changed", t)
            }
        }
        try {
            msm.addOnActiveSessionsChangedListener(listener, component, handler)
            bind(msm.getActiveSessions(component))
        } catch (t: Throwable) {
            // SecurityException when Notification access isn't granted.
            runCatching { msm.removeOnActiveSessionsChangedListener(listener) }
            trySend(Report.NO_ACCESS)
            close()
            return@callbackFlow
        }
        // Access can be revoked silently; end the flow so the caller re-checks.
        launch {
            while (true) {
                delay(REVOKE_CHECK_MS)
                if (!MediaAccessStatus.isGranted(app)) {
                    trySend(Report.NO_ACCESS)
                    close()
                    break
                }
            }
        }
        awaitClose {
            runCatching { msm.removeOnActiveSessionsChangedListener(listener) }
            for ((_, pair) in bound) runCatching { pair.first.unregisterCallback(pair.second) }
            bound.clear()
            controllers = emptyList()
        }
    }.flowOn(Dispatchers.Main)

    private fun sampleAll(list: List<MediaController>): Report {
        val sessions = ArrayList<NowPlaying.Session>(list.size)
        val art = HashMap<String, Bitmap?>()
        for (c in list) {
            val s = sample(c) ?: continue
            sessions += s
            if (!art.containsKey(s.pkg)) art[s.pkg] = artOf(c.metadata)
        }
        return Report(true, sessions, art)
    }

    private fun sample(c: MediaController): NowPlaying.Session? = try {
        // A session that only remote-controls another device (e.g. Spotify Connect mirroring your phone)
        // is not audio playing on this box, so it must not show up as "now playing" here.
        val remote = c.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
        val pkg = c.packageName
        val st = c.playbackState
        val md = c.metadata
        if (remote) null else NowPlaying.Session(
            pkg = pkg,
            app = labelOf(pkg),
            state = st?.state ?: PlaybackState.STATE_NONE,
            title = md?.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.text(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            subtitle = md?.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: md?.text(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.text(MediaMetadata.METADATA_KEY_ALBUM)
                ?: "",
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = st?.position ?: 0,
            lastPositionUpdateMs = st?.lastPositionUpdateTime ?: 0,
            speed = st?.playbackSpeed ?: 0f,
            actions = st?.actions ?: 0,
        )
    } catch (t: Throwable) {
        log("sample", t)
        null
    }

    private fun MediaMetadata.text(key: String): String? =
        runCatching { getText(key)?.toString()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }?.take(300)

    private fun artOf(md: MediaMetadata?): Bitmap? {
        if (md == null) return null
        for (k in ART_KEYS) {
            val b = runCatching { md.getBitmap(k) }.getOrNull()
            if (b != null) return b
        }
        return null
    }

    /** The controller the phone is looking at: the chosen session, else the first active one. Main thread. */
    private fun target(): Pair<MediaController, NowPlaying.Session>? {
        val pairs = controllers.mapNotNull { c -> sample(c)?.let { c to it } }
        val chosen = NowPlaying.choose(pairs.map { it.second })
        return pairs.firstOrNull { it.second == chosen } ?: pairs.firstOrNull()
    }

    /** Applies a `media` command. False if there is no session (or no access). */
    suspend fun transport(action: RemoteCommand.MediaAction, deltaMs: Long?): Boolean = withContext(Dispatchers.Main) {
        try {
            val (c, s) = target() ?: return@withContext false
            val t = c.transportControls
            when (action) {
                RemoteCommand.MediaAction.Toggle -> if (s.isPlaying) t.pause() else t.play()
                RemoteCommand.MediaAction.Play -> t.play()
                RemoteCommand.MediaAction.Pause -> t.pause()
                RemoteCommand.MediaAction.Next -> t.skipToNext()
                RemoteCommand.MediaAction.Prev -> t.skipToPrevious()
                RemoteCommand.MediaAction.Seek -> {
                    if (deltaMs == null || !s.canSeek) return@withContext false
                    val cur = NowPlaying.positionAt(s, SystemClock.elapsedRealtime())
                    t.seekTo(NowPlaying.seekTarget(cur, deltaMs, s.durationMs))
                }
            }
            true
        } catch (t: Throwable) {
            log("transport", t)
            false
        }
    }

    /** Sleep-timer expiry: pause whatever is playing. */
    suspend fun pausePlaying(): Unit = withContext(Dispatchers.Main) {
        for (c in controllers) {
            try {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            } catch (t: Throwable) {
                log("pause", t)
            }
        }
    }

    private fun log(what: String, t: Throwable) {
        Log.w(TAG, "$what: ${t.javaClass.simpleName}: ${t.message}")
    }

    companion object {
        private const val TAG = "LoungeMedia"
        private const val REVOKE_CHECK_MS = 30_000L
        const val ART_MAX = 320

        private val ART_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )

        /** Art scaled to fit 320×320 as JPEG bytes; null if it can't be encoded. */
        fun encodeArt(src: Bitmap): ByteArray? = try {
            val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            val (w, h) = NowPlaying.fitWithin(soft.width, soft.height, ART_MAX)
            val scaled = if (w == soft.width && h == soft.height) soft else Bitmap.createScaledBitmap(soft, w, h, true)
            val out = ByteArrayOutputStream()
            val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== soft && scaled !== src) scaled.recycle()
            if (soft !== src) soft.recycle()
            if (ok) out.toByteArray() else null
        } catch (t: Throwable) {
            Log.w(TAG, "encodeArt: ${t.javaClass.simpleName}")
            null
        }
    }
}
