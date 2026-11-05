package ca.cheungeric.lounge.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Persists [MediaHistory] (SPEC "v6 → Continue card"): the list as JSON in `lounge_prefs`, art as
 * PNGs (max 400 px wide) in filesDir/media_art. Process-wide (AppContainer); IO never throws.
 */
class MediaHistoryStore(context: Context) {
    private val app = context.applicationContext
    private val prefs get() = app.getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE)
    private val artDir get() = File(app.filesDir, ART_DIR)
    private val mutex = Mutex()

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    /** Absolute art file for an entry, or null when it has none / it's missing. */
    fun artFileOf(e: HistoryEntry): File? = e.artFile?.let { File(artDir, it) }?.takeIf { it.isFile }

    /** Records a PLAYING session with a non-blank title. Lounge's own package is ignored. */
    suspend fun record(packageName: String, appLabel: String, title: String, subtitle: String?, art: Bitmap?) {
        if (title.isBlank() || packageName == app.packageName) return
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = _entries.value
                val existing = current.firstOrNull { it.sameItem(packageName, title) }
                // Art is saved once per item (the name is stable); later frames reuse it.
                val artName = if (art != null && existing?.artFile == null) saveArt(packageName, title, art) else null
                val u = MediaHistory.upsert(current, packageName, appLabel, title, subtitle, artName, System.currentTimeMillis())
                if (!u.changed) return@withLock
                _entries.value = u.entries
                persist(u.entries)
                u.evicted.forEach { deleteArt(it.artFile) }
            }
        }
    }

    suspend fun remove(e: HistoryEntry) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val (list, gone) = MediaHistory.remove(_entries.value, e.packageName, e.title)
                if (gone == null) return@withLock
                _entries.value = list
                persist(list)
                if (list.none { it.artFile == gone.artFile }) deleteArt(gone.artFile)
            }
        }
    }

    /** Deletes art files nobody references (call once off the main thread). */
    suspend fun cleanOrphans() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val names = try { artDir.list()?.toList() } catch (_: Throwable) { null } ?: return@withLock
                MediaHistory.orphanArt(_entries.value, names).forEach(::deleteArt)
            }
        }
    }

    private fun load(): List<HistoryEntry> = try {
        MediaHistory.decode(prefs.getString(KEY, null))
    } catch (_: Throwable) {
        emptyList()
    }

    private fun persist(list: List<HistoryEntry>) {
        try {
            prefs.edit().putString(KEY, MediaHistory.encode(list)).apply()
        } catch (t: Throwable) {
            log("persist", t)
        }
    }

    private fun saveArt(pkg: String, title: String, src: Bitmap): String? {
        val name = MediaHistory.artFileName(pkg, title)
        return try {
            val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
            val scaled = if (soft.width > ART_MAX_WIDTH) {
                val h = (soft.height.toLong() * ART_MAX_WIDTH / soft.width).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(soft, ART_MAX_WIDTH, h, true)
            } else {
                soft
            }
            val dir = artDir
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val dest = File(dir, name)
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return null
            }
            name
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            log("saveArt", t)
            null
        }
    }

    private fun deleteArt(name: String?) {
        if (name.isNullOrBlank() || name.contains('/') || name.contains('\\')) return
        try {
            File(artDir, name).delete()
        } catch (_: Throwable) {
        }
    }

    private fun log(what: String, t: Throwable) {
        Log.w(TAG, "$what: ${t.javaClass.simpleName}")
    }

    companion object {
        private const val TAG = "LoungeHistory"
        const val KEY = "lounge_mediaHistory"
        const val ART_DIR = "media_art"
        const val ART_MAX_WIDTH = 400
    }
}
