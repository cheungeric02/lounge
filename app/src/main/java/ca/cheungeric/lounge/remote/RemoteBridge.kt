package ca.cheungeric.lounge.remote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import ca.cheungeric.lounge.data.AppEntry
import ca.cheungeric.lounge.data.AppsRepository
import ca.cheungeric.lounge.data.HomeLayout
import ca.cheungeric.lounge.data.LayoutPrefs
import ca.cheungeric.lounge.data.LayoutPrefsState
import ca.cheungeric.lounge.home.HomeButtonService
import ca.cheungeric.lounge.home.HomeServiceStatus
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
        val session = Session(base)
        launch { statusLoop(base) }
        launch { publishApps(session) }
        launch { publishLayout(base) }
        launch { commandLoop(base) }
    }

    /** Per-run state (a new key starts from an empty node). */
    private class Session(val base: String) {
        /** pkg → iconHash already uploaded to tv/icons. */
        val uploaded = HashMap<String, String>()
        var seeded = false
        var lastAppsJson: String? = null
    }

    private suspend fun statusLoop(base: String) {
        while (true) {
            try {
                db.put("$base/tv/status", RemotePayloads.status(APP_VERSION, HomeServiceStatus.isEnabled(app)))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                log("status", t)
            }
            delay(STATUS_INTERVAL_MS)
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

    private suspend fun commandLoop(base: String) = coroutineScope {
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
                        apply(cmd)
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

    private suspend fun apply(cmd: RemoteCommand) {
        val apps = appsRepository.apps.value
        val installed = apps.map { it.packageName }
        when (cmd) {
            is RemoteCommand.Launch -> {
                val intent = apps.firstOrNull { it.packageName == cmd.pkg }?.launchIntent ?: return
                withContext(Dispatchers.Main) {
                    app.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            RemoteCommand.Home -> withContext(Dispatchers.Main) {
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
        private const val DEBOUNCE_MS = 500L
        private const val BACKOFF_MIN_MS = 2_000L
        private const val BACKOFF_MAX_MS = 60_000L
        private const val DELETE_TIMEOUT_MS = 20_000L
    }
}
