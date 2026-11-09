package ca.cheungeric.lounge.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import ca.cheungeric.lounge.R
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.AppsRepository
import ca.cheungeric.lounge.data.HomeLayout
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.LayoutPrefsState
import ca.cheungeric.lounge.home.HomeButtonService
import ca.cheungeric.lounge.home.HomeServiceStatus
import ca.cheungeric.lounge.home.MediaAccessStatus
import ca.cheungeric.lounge.ui.patchnotes.APP_VERSION
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream

enum class RemoteStatus { Off, Connecting, Connected, Offline }

/**
 * Phone-remote bridge (SPEC "v2 → TV side"). Process-wide, owned by AppContainer, ref-counted:
 * runs only while acquired AND `lounge_remoteEnabled` is on AND `lounge_remoteKey` is valid.
 * Publishes tv/status, tv/apps, tv/layout, tv/icons and consumes cmd/ under `lounge/<key>/` only.
 * Never throws to callers; all network errors are logged and retried with backoff.
 */
class RemoteBridge(
    context: Context,
    private val appsRepository: AppsRepository,
    private val layoutPrefs: LayoutPrefs,
    private val bravia: BraviaControl,
    private val db: RemoteDb = FirebaseRest(),
) {
    private val app = context.applicationContext
    private val prefs get() = app.getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t -> log("uncaught", t) },
    )

    private val lock = Any()
    private var refs = 0
    private var enabled: Boolean = readEnabled()
    private var job: Job? = null
    private var runningKey: String? = null
    private var deleteJob: Job? = null

    private val mediaWatcher = MediaWatcher(app, ::labelFor)

    /** Last access result from [MediaWatcher] (tv/status `mediaAccess`). */
    @Volatile
    private var mediaAccess = false

    private val _key = MutableStateFlow(readKey())
    val key: StateFlow<String?> = _key.asStateFlow()

    private val _status = MutableStateFlow(RemoteStatus.Off)
    val status: StateFlow<RemoteStatus> = _status.asStateFlow()

    val isEnabled: Boolean get() = synchronized(lock) { enabled }

    fun acquire() {
        synchronized(lock) {
            refs++
            reevaluate()
        }
    }

    fun release() {
        synchronized(lock) {
            if (refs > 0) refs--
            reevaluate()
        }
    }

    /** Settings → Phone remote on/off. Generates a key on first enable. */
    fun setEnabled(on: Boolean) {
        synchronized(lock) {
            enabled = on
            if (on && !RemoteKey.isValid(_key.value)) {
                val k = RemoteKey.generate()
                writeKey(k)
                _key.value = k
            }
            try {
                prefs.edit().putBoolean(KEY_ENABLED, on).apply()
            } catch (t: Throwable) {
                log("write enabled", t)
            }
            reevaluate()
        }
    }

    /** New pairing key: stops the bridge, DELETEs the old `lounge/<old>` node, then restarts on the new key. */
    fun regenerateKey() {
        synchronized(lock) {
            val old = _key.value
            var k = RemoteKey.generate()
            while (k == old) k = RemoteKey.generate()
            writeKey(k)
            _key.value = k
            val prev = job
            prev?.cancel()
            job = null
            runningKey = null
            if (RemoteKey.isValid(old)) {
                val prevDelete = deleteJob
                deleteJob = scope.launch {
                    prev?.join()
                    prevDelete?.join()
                    try {
                        withTimeout(DELETE_TIMEOUT_MS) { db.delete("lounge/$old") }
                    } catch (e: TimeoutCancellationException) {
                        log("delete old node timed out", e)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        log("delete old node", t)
                    }
                }
            }
            reevaluate()
        }
    }

    /** Must hold [lock]. Starts/stops/restarts the run job to match the desired state. */
    private fun reevaluate() {
        val k = _key.value
        val want = refs > 0 && enabled && RemoteKey.isValid(k)
        if (want && job?.isActive == true && runningKey == k) return
        val prev = job
        prev?.cancel()
        job = null
        runningKey = null
        if (!want || k == null) {
            _status.value = RemoteStatus.Off
            return
        }
        val gate = deleteJob
        runningKey = k
        _status.value = RemoteStatus.Connecting
        job = scope.launch {
            prev?.join()
            gate?.join()
            try {
                run(k)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("bridge run", t)
            }
        }
    }

    // ---- running ----

    private suspend fun run(key: String) = coroutineScope {
        val base = "lounge/$key"
        val session = Session(base, this)
        launch { statusLoop(base) }
        launch { publishApps(session) }
        launch { publishLayout(base) }
        launch { commandLoop(session) }
        launch { mediaLoop(session) }
        launch { volumeLoop(session) }
        launch { inputLoop(base) }
        launch { retrying("sleep rearm") { rearmSleep(session) } }
        launch { powerLoop(session) }
    }

    /** Per-run state (a new key starts from an empty node). */
    private class Session(val base: String, val scope: CoroutineScope) {
        /** pkg → iconHash already uploaded to tv/icons. */
        val uploaded = HashMap<String, String>()
        var seeded = false
        var lastAppsJson: String? = null

        // v3. Media fields: media coroutine only. Volume: volume coroutine. Sleep: command coroutine + its job.
        /** sha1 of the tv/art bytes on the server; null = unknown (first publish deletes stale art). */
        var artHash: String? = null
        var artSrc: Bitmap? = null
        var artBytes: ByteArray? = null
        var lastChosen: NowPlaying.Session? = null
        var nowPlayingPublished = false
        var lastVolumeJson: String? = null
        val volumeKick = Channel<Unit>(Channel.CONFLATED)

        @Volatile
        var sleepJob: Job? = null

        // v5: power coroutine only (kicked by power/input commands).
        val powerKick = Channel<Unit>(Channel.CONFLATED)
        val powerMutex = Mutex()
        /** Last tv/power JSON written; "" = known deleted; null = unknown (first pass deletes/writes). */
        var lastPowerJson: String? = null
    }

    /** Writes tv/status at once when a field changes, else every 60 s (lastSeen heartbeat). */
    private suspend fun statusLoop(base: String) {
        var last: String? = null
        var lastAt = 0L
        while (true) {
            val json = RemotePayloads.status(
                APP_VERSION,
                HomeServiceStatus.isEnabled(app),
                mediaAccess,
                HomeButtonService.isRunning(),
                keys = HomeButtonService.isKeysSupported(),
                power = powerMode(),
            )
            val now = SystemClock.elapsedRealtime()
            if (json != last || now - lastAt >= STATUS_INTERVAL_MS) {
                try {
                    db.put("$base/tv/status", json)
                    last = json
                    lastAt = now
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    log("status", t)
                }
            }
            delay(STATUS_CHECK_MS)
        }
    }

    // ---- v5: power ----

    /** tv/status `power`: Bravia configured + last call OK → bravia; else box sleep via a11y → sleep; else none. */
    private fun powerMode(): String = when {
        bravia.isConfigured && bravia.lastOk -> RemotePayloads.POWER_BRAVIA
        HomeButtonService.isRunning() -> RemotePayloads.POWER_SLEEP
        else -> RemotePayloads.POWER_NONE
    }

    /**
     * Polls Bravia power every 15 s (or right after a power/input command) and publishes tv/power
     * while [powerMode] is bravia; deletes it otherwise.
     */
    private suspend fun powerLoop(s: Session) {
        while (true) {
            try {
                val state = if (bravia.isConfigured) {
                    (bravia.powerStatus() as? BraviaControl.Result.Ok)?.state
                } else {
                    null
                }
                if (bravia.isConfigured && bravia.lastOk) {
                    val json = RemotePayloads.power((state ?: BraviaRpc.PowerState.Unknown).wire)
                    if (json != s.lastPowerJson) {
                        db.put("${s.base}/tv/power", json)
                        s.lastPowerJson = json
                    }
                } else if (s.lastPowerJson != "") {
                    db.delete("${s.base}/tv/power")
                    s.lastPowerJson = ""
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("power", t)
            }
            withTimeoutOrNull(POWER_POLL_MS) { s.powerKick.receive() }
        }
    }

    /** Runs a Bravia/power command on the run scope, one at a time, never failing the run. */
    private fun tvControl(s: Session, what: String, block: suspend () -> Unit) {
        s.scope.launch {
            try {
                s.powerMutex.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log(what, t)
            }
        }
    }

    private suspend fun power(s: Session, action: RemoteCommand.PowerAction) {
        if (!bravia.isConfigured) {
            // No IP control: the box can sleep (CEC may turn the TV off); turning on is impossible.
            if (action != RemoteCommand.PowerAction.On) {
                withContext(Dispatchers.Main) { HomeButtonService.sleepBox() }
            }
            s.powerKick.trySend(Unit)
            return
        }
        val r = when (action) {
            RemoteCommand.PowerAction.Toggle -> bravia.toggle()
            RemoteCommand.PowerAction.On -> bravia.setPower(true)
            RemoteCommand.PowerAction.Off -> bravia.setPower(false)
        }
        if (r !is BraviaControl.Result.Ok) log("power ${action.name}", IllegalStateException(r.javaClass.simpleName))
        delay(POWER_SETTLE_MS)
        s.powerKick.trySend(Unit)
    }

    private suspend fun input(s: Session, port: Int) {
        if (!bravia.isConfigured) return
        val r = bravia.setInput(port)
        if (r !is BraviaControl.Result.Ok) log("input", IllegalStateException(r.javaClass.simpleName))
        s.powerKick.trySend(Unit)
    }

    /** v7: phone Home also puts the TV on the box's HDMI port (no-op without Bravia control). */
    private fun showBoxInput(s: Session) {
        if (bravia.isConfigured) tvControl(s, "home input") { input(s, bravia.boxPort) }
    }

    private suspend fun key(s: Session, k: RemoteCommand.KeyName) {
        when (k) {
            RemoteCommand.KeyName.Back -> withContext(Dispatchers.Main) { HomeButtonService.performBack() }
            RemoteCommand.KeyName.Home -> withContext(Dispatchers.Main) {
                showBoxInput(s)
                app.startActivity(HomeButtonService.loungeIntent(app))
            }
            else -> withContext(Dispatchers.Main) { HomeButtonService.dpad(k) }
        }
    }

    // ---- v3: Now Playing ----

    @OptIn(FlowPreview::class)
    private suspend fun mediaLoop(s: Session) {
        while (true) {
            try {
                mediaWatcher.reports()
                    .distinctUntilChanged()
                    .debounce(MEDIA_DEBOUNCE_MS)
                    .collectLatest { r ->
                        mediaAccess = r.access
                        retrying("nowPlaying") { publishMedia(s, r) }
                        // A paused session expires on its own: re-evaluate when its TTL runs out.
                        val paused = r.sessions.filter { it.isPaused && it.lastPositionUpdateMs > 0 }.maxOfOrNull { it.lastPositionUpdateMs }
                        if (r.access && paused != null && r.sessions.none { it.isPlaying }) {
                            val wait = paused + NowPlaying.PAUSED_TTL_MS - SystemClock.elapsedRealtime() + 1_000
                            if (wait > 0) {
                                delay(wait)
                                retrying("nowPlaying") { publishMedia(s, r) }
                            }
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("media", t)
            }
            // Flow ended: no access (or revoked). Status shows mediaAccess:false; check again later.
            if (!MediaAccessStatus.isGranted(app)) mediaAccess = false
            delay(MEDIA_RETRY_MS)
        }
    }

    private suspend fun publishMedia(s: Session, r: MediaWatcher.Report) {
        val chosen = if (r.access) NowPlaying.choose(r.sessions, SystemClock.elapsedRealtime()) else null
        val bmp = chosen?.let { r.art[it.pkg] }
        val bytes = when {
            bmp == null -> null
            bmp === s.artSrc -> s.artBytes
            else -> withContext(Dispatchers.Default) { MediaWatcher.encodeArt(bmp) }.also {
                s.artSrc = bmp
                s.artBytes = it
            }
        }
        val hash = bytes?.let(RemotePayloads::sha1Hex) ?: ""
        val artChanged = hash != s.artHash
        if (!artChanged && s.nowPlayingPublished && !NowPlaying.needsRepublish(s.lastChosen, chosen)) return
        if (artChanged) {
            // Art first, so a published artHash always has its art.
            if (bytes != null) {
                db.put("${s.base}/tv/art", RemotePayloads.jsonString(RemotePayloads.jpegDataUri(bytes)))
            } else {
                db.delete("${s.base}/tv/art")
            }
            s.artHash = hash
        }
        db.put(
            "${s.base}/tv/nowPlaying",
            NowPlaying.payload(chosen, hash, SystemClock.elapsedRealtime(), db.serverNowMs()),
        )
        s.lastChosen = chosen
        s.nowPlayingPublished = true
    }

    // ---- v3: volume ----

    private suspend fun volumeLoop(s: Session) {
        while (true) {
            try {
                volumeJson()?.let { json ->
                    if (json != s.lastVolumeJson) {
                        db.put("${s.base}/tv/volume", json)
                        s.lastVolumeJson = json
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("volume", t)
            }
            withTimeoutOrNull(VOLUME_POLL_MS) { s.volumeKick.receive() }
        }
    }

    private fun audio(): AudioManager? = try {
        app.getSystemService(AudioManager::class.java)
    } catch (t: Throwable) {
        null
    }

    private suspend fun volumeJson(): String? {
        // The Bravia's speaker is what you hear, so prefer it; the box's own stream is only a fallback.
        bravia.volume()?.let { return RemotePayloads.volume(it.level, it.max, it.muted) }
        val am = audio() ?: return null
        return try {
            RemotePayloads.volume(
                am.getStreamVolume(AudioManager.STREAM_MUSIC),
                am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                am.isStreamMute(AudioManager.STREAM_MUSIC),
            )
        } catch (t: Throwable) {
            log("read volume", t)
            null
        }
    }

    private suspend fun adjustVolume(s: Session, action: RemoteCommand.VolumeAction) {
        val viaTv = when (action) {
            RemoteCommand.VolumeAction.Up -> bravia.adjustVolume(VOLUME_TV_STEP)
            RemoteCommand.VolumeAction.Down -> bravia.adjustVolume(-VOLUME_TV_STEP)
            RemoteCommand.VolumeAction.Mute -> bravia.toggleMute()
        }
        if (viaTv) {
            delay(VOLUME_SETTLE_MS)
            s.volumeKick.trySend(Unit)
            return
        }
        val am = audio() ?: return
        val dir = when (action) {
            RemoteCommand.VolumeAction.Up -> AudioManager.ADJUST_RAISE
            RemoteCommand.VolumeAction.Down -> AudioManager.ADJUST_LOWER
            RemoteCommand.VolumeAction.Mute -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        withContext(Dispatchers.Main) {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        }
        delay(VOLUME_SETTLE_MS)
        s.volumeKick.trySend(Unit)
    }

    // ---- v3: typing ----

    @OptIn(FlowPreview::class)
    private suspend fun inputLoop(base: String) {
        TvInput.state
            .debounce(INPUT_DEBOUNCE_MS)
            .map(TvInput::payload)
            .distinctUntilChanged()
            .collectLatest { json -> retrying("input") { db.put("$base/tv/input", json) } }
    }

    // ---- v3: sleep timer ----

    /** Bridge (re)start: re-arm a still-future tv/sleep, delete an expired one (never fire late). */
    private suspend fun rearmSleep(s: Session) {
        val json = db.get("${s.base}/tv/sleep")
        when (val d = SleepTimer.rearm(json, db.serverNowMs())) {
            SleepTimer.Rearm.None -> Unit
            SleepTimer.Rearm.Clear -> if (s.sleepJob == null) db.delete("${s.base}/tv/sleep")
            is SleepTimer.Rearm.Arm -> if (s.sleepJob == null) armSleep(s, d.delayMs)
        }
    }

    private suspend fun setSleep(s: Session, minutes: Int) {
        s.sleepJob?.cancel()
        s.sleepJob = null
        val now = db.serverNowMs()
        val at = SleepTimer.atFor(minutes, now)
        if (at == null) {
            db.delete("${s.base}/tv/sleep")
            return
        }
        armSleep(s, at - now)
        db.put("${s.base}/tv/sleep", RemotePayloads.sleep(at))
    }

    private fun armSleep(s: Session, delayMs: Long) {
        s.sleepJob?.cancel()
        s.sleepJob = s.scope.launch {
            delay(delayMs.coerceAtLeast(0))
            try {
                onSleepExpired()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("sleep expiry", t)
            }
            retrying("sleep clear") { db.delete("${s.base}/tv/sleep") }
        }
    }

    /** Pause playback, go to Lounge Home, then try the screensaver (failure ignored). */
    private suspend fun onSleepExpired() {
        mediaWatcher.pausePlaying()
        withContext(Dispatchers.Main) {
            runCatching { app.startActivity(HomeButtonService.loungeIntent(app)) }
        }
        delay(SCREENSAVER_DELAY_MS)
        withContext(Dispatchers.Main) {
            runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setComponent(ComponentName(SYSTEMUI_PKG, "$SYSTEMUI_PKG.Somnambulator"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    // ---- v3: open link ----

    private suspend fun openLink(url: String) {
        val route = LinkRouter.route(url) ?: return
        withContext(Dispatchers.Main) {
            val pm = app.packageManager
            for (pkg in route.targets) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(route.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pkg != null) intent.setPackage(pkg)
                val resolves = runCatching { pm.resolveActivity(intent, 0) != null }.getOrDefault(false)
                if (!resolves) continue
                try {
                    app.startActivity(intent)
                    return@withContext
                } catch (t: Throwable) {
                    log("open $pkg", t)
                }
            }
            runCatching { Toast.makeText(app, R.string.remote_cant_open_link, Toast.LENGTH_LONG).show() }
        }
    }

    private fun labelFor(pkg: String): String {
        appsRepository.apps.value.firstOrNull { it.packageName == pkg }?.let { return it.label }
        return try {
            val pm = app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (t: Throwable) {
            pkg
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun publishApps(session: Session) {
        appsRepository.apps
            .filter { it.isNotEmpty() }
            .debounce(DEBOUNCE_MS)
            .collectLatest { apps -> retrying("apps") { syncApps(session, apps) } }
    }

    private suspend fun syncApps(s: Session, apps: List<AppEntry>) {
        if (!s.seeded) {
            seedUploaded(s)
            s.seeded = true
        }
        val icons = withContext(Dispatchers.Default) {
            apps.distinctBy { it.packageName }.mapNotNull { a -> renderPng(a)?.let { a.packageName to it } }.toMap()
        }
        val hashes = icons.mapValues { RemotePayloads.sha1Hex(it.value) }
        // Icons first, so a published iconHash always has its icon.
        for ((pkg, png) in icons) {
            val h = hashes.getValue(pkg)
            if (s.uploaded[pkg] == h) continue
            db.put("${s.base}/tv/icons/${RemoteCommand.pkgKey(pkg)}", RemotePayloads.jsonString(RemotePayloads.pngDataUri(png)))
            s.uploaded[pkg] = h
        }
        val json = RemotePayloads.apps(apps, hashes)
        if (json != s.lastAppsJson) {
            db.put("${s.base}/tv/apps", json)
            s.lastAppsJson = json
        }
        val installed = apps.mapTo(HashSet()) { it.packageName }
        for (pkg in s.uploaded.keys.filter { it !in installed || it !in icons }) {
            db.delete("${s.base}/tv/icons/${RemoteCommand.pkgKey(pkg)}")
            s.uploaded.remove(pkg)
        }
    }

    /** Learn which icons are already on the server from a previous run (saves re-uploading all). */
    private suspend fun seedUploaded(s: Session) {
        try {
            val obj = Json.parseToJsonElement(db.get("${s.base}/tv/apps")) as? JsonObject ?: return
            for ((_, v) in obj) {
                val o = v as? JsonObject ?: continue
                val pkg = (o["pkg"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                val h = (o["iconHash"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
                if (h.isNotEmpty()) s.uploaded[pkg] = h
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log("seed icons", t)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun publishLayout(base: String) {
        combine(appsRepository.apps.filter { it.isNotEmpty() }, layoutPrefs.state) { apps, p ->
            RemotePayloads.layout(apps, p)
        }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)
            .collectLatest { json -> retrying("layout") { db.put("$base/tv/layout", json) } }
    }

    private suspend fun commandLoop(s: Session) = coroutineScope {
        val base = s.base
        val events = Channel<SseEvent>(Channel.UNLIMITED)
        val handled = object : LinkedHashMap<String, Boolean>(64, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 256
        }
        launch {
            for (ev in events) {
                for ((id, el) in RemoteCommand.childrenOf(ev)) {
                    if (handled.containsKey(id)) continue
                    handled[id] = true
                    try {
                        db.delete("$base/cmd/$id")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        log("delete cmd", t)
                    }
                    val cmd = RemoteCommand.parseFresh(el, db.serverNowMs()) ?: continue
                    try {
                        apply(cmd, s)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (t: Throwable) {
                        log("apply ${cmd.javaClass.simpleName}", t)
                    }
                }
            }
        }
        var backoff = BACKOFF_MIN_MS
        while (true) {
            _status.value = RemoteStatus.Connecting
            try {
                db.stream("$base/cmd") { ev ->
                    when (ev) {
                        SseEvent.Cancel, SseEvent.AuthRevoked -> throw StreamClosed(ev.toString())
                        else -> {
                            if (_status.value != RemoteStatus.Connected) _status.value = RemoteStatus.Connected
                            backoff = BACKOFF_MIN_MS
                            events.trySend(ev)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // A cancelled run closes the socket, which surfaces as an IOException: stop quietly.
                currentCoroutineContext().ensureActive()
                log("stream", t)
            }
            currentCoroutineContext().ensureActive()
            _status.value = RemoteStatus.Offline
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
        }
    }

    private class StreamClosed(msg: String) : Exception(msg)

    private suspend fun apply(cmd: RemoteCommand, s: Session) {
        val apps = appsRepository.apps.value
        val installed = apps.map { it.packageName }
        when (cmd) {
            is RemoteCommand.Launch -> {
                val intent = apps.firstOrNull { it.packageName == cmd.pkg }?.launchIntent ?: return
                withContext(Dispatchers.Main) {
                    app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    val profile = cmd.profile
                    if (profile != null && cmd.pkg in ProfilePicker.SUPPORTED) {
                        HomeButtonService.armProfilePick(cmd.pkg, profile)
                    }
                }
            }
            RemoteCommand.Home -> withContext(Dispatchers.Main) {
                showBoxInput(s)
                app.startActivity(HomeButtonService.loungeIntent(app))
            }
            RemoteCommand.Back -> withContext(Dispatchers.Main) { HomeButtonService.performBack() }
            is RemoteCommand.Favorite -> edit(cmd.pkg, installed) {
                if (cmd.pkg in it.favorites) it else HomeLayout.toggleFavorite(it, cmd.pkg)
            }
            is RemoteCommand.Unfavorite -> edit(cmd.pkg, installed) {
                if (cmd.pkg in it.favorites) HomeLayout.toggleFavorite(it, cmd.pkg) else it
            }
            is RemoteCommand.Hide -> edit(cmd.pkg, installed) {
                if (cmd.pkg in it.hidden) it else HomeLayout.hide(it, cmd.pkg)
            }
            is RemoteCommand.Unhide -> edit(cmd.pkg, installed) { HomeLayout.unhide(it, cmd.pkg) }
            is RemoteCommand.MoveFavorite -> edit(cmd.pkg, installed) {
                HomeLayout.moveFavoriteTo(it, cmd.pkg, cmd.to, installed)
            }
            is RemoteCommand.Media -> mediaWatcher.transport(cmd.action, cmd.ms)
            is RemoteCommand.Volume -> adjustVolume(s, cmd.action)
            is RemoteCommand.Sleep -> setSleep(s, cmd.minutes)
            is RemoteCommand.Open -> openLink(cmd.url)
            is RemoteCommand.Text -> withContext(Dispatchers.Main) { HomeButtonService.setText(cmd.text) }
            RemoteCommand.Submit -> withContext(Dispatchers.Main) { HomeButtonService.submit() }
            // LAN calls can take seconds: run them off the command queue so D-pad keys never wait.
            is RemoteCommand.Power -> tvControl(s, "power") { power(s, cmd.action) }
            is RemoteCommand.Input -> tvControl(s, "input") { input(s, cmd.port) }
            is RemoteCommand.Key -> key(s, cmd.key)
        }
    }

    /** Layout edit for an installed [pkg]; seeds first-run defaults first so Home won't overwrite it. */
    private suspend fun edit(pkg: String, installed: List<String>, transform: (LayoutPrefsState) -> LayoutPrefsState) {
        if (pkg !in installed) return
        layoutPrefs.update { p -> transform(HomeLayout.initDefaults(p, installed)) }
    }

    /** Runs [block] until it succeeds, backing off 2 s → 60 s; cancelled by a newer value. */
    private suspend fun retrying(what: String, block: suspend () -> Unit) {
        var backoff = BACKOFF_MIN_MS
        while (true) {
            try {
                block()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log(what, t)
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
        }
    }

    /** Banner → 192×108, else icon → 96×96, as PNG bytes; null if neither renders. */
    private fun renderPng(a: AppEntry): ByteArray? {
        val (d, w, h) = when {
            a.banner != null -> Triple(a.banner, 192, 108)
            a.icon != null -> Triple(a.icon, 96, 96)
            else -> return null
        }
        return try {
            // Draw a private copy: the UI draws the shared Drawable on the main thread.
            val copy: Drawable = d.constantState?.newDrawable(app.resources)?.mutate() ?: return null
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            copy.setBounds(0, 0, w, h)
            copy.draw(Canvas(bmp))
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        } catch (t: Throwable) {
            log("render icon", t)
            null
        }
    }

    // ---- prefs ----

    private fun readEnabled(): Boolean = try {
        prefs.getBoolean(KEY_ENABLED, false)
    } catch (_: Throwable) {
        false
    }

    private fun readKey(): String? = try {
        prefs.getString(KEY_KEY, null)?.takeIf { RemoteKey.isValid(it) }
    } catch (_: Throwable) {
        null
    }

    private fun writeKey(k: String) {
        try {
            prefs.edit().putString(KEY_KEY, k).apply()
        } catch (t: Throwable) {
            log("write key", t)
        }
    }

    private fun log(what: String, t: Throwable) {
        Log.w(TAG, "$what: ${t.javaClass.simpleName}: ${t.message}")
    }

    companion object {
        private const val TAG = "LoungeRemote"
        const val KEY_ENABLED = "lounge_remoteEnabled"
        const val KEY_KEY = "lounge_remoteKey"
        private const val STATUS_INTERVAL_MS = 60_000L
        private const val STATUS_CHECK_MS = 5_000L
        private const val MEDIA_DEBOUNCE_MS = 300L
        private const val MEDIA_RETRY_MS = 10_000L
        private const val VOLUME_POLL_MS = 5_000L
        private const val VOLUME_SETTLE_MS = 150L
        /** Bravia speaker step per press (0-100 scale). */
        private const val VOLUME_TV_STEP = 2
        private const val POWER_POLL_MS = 15_000L
        private const val POWER_SETTLE_MS = 1_000L
        private const val INPUT_DEBOUNCE_MS = 200L
        private const val SCREENSAVER_DELAY_MS = 800L
        private const val SYSTEMUI_PKG = "com.android.systemui"
        private const val DEBOUNCE_MS = 500L
        private const val BACKOFF_MIN_MS = 2_000L
        private const val BACKOFF_MAX_MS = 60_000L
        private const val DELETE_TIMEOUT_MS = 20_000L
    }
}
