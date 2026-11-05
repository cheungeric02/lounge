package ca.cheungeric.lounge.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** One "recently watched" item (SPEC "v6 → Continue card"). [artFile] is a name in filesDir/media_art. */
@Serializable
data class HistoryEntry(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String? = null,
    val artFile: String? = null,
    val updatedAtMs: Long = 0,
) {
    fun sameItem(pkg: String, title: String): Boolean = packageName == pkg && this.title == title
}

/** Pure history logic: upsert / trim / dedupe / remove, JSON codec, art names and relative ages. */
object MediaHistory {
    const val MAX_ENTRIES = 12
    const val WRITE_THROTTLE_MS = 30_000L

    /**
     * Result of [upsert]. [changed] = the list must be written to disk; [evicted] = entries dropped
     * by the trim (their art files should be deleted).
     */
    data class Upsert(val entries: List<HistoryEntry>, val changed: Boolean, val evicted: List<HistoryEntry>)

    /**
     * Moves (packageName, title) to the front with [nowMs], keeping an existing art file unless
     * [artFile] replaces it. Skips the write when the item is already first, unchanged and was written
     * less than [WRITE_THROTTLE_MS] ago. Drops duplicates and keeps at most [MAX_ENTRIES].
     */
    fun upsert(
        list: List<HistoryEntry>,
        packageName: String,
        appLabel: String,
        title: String,
        subtitle: String?,
        artFile: String?,
        nowMs: Long,
    ): Upsert {
        val sub = subtitle?.takeIf { it.isNotBlank() }
        val existing = list.firstOrNull { it.sameItem(packageName, title) }
        val first = list.firstOrNull()
        if (existing != null && existing === first &&
            existing.subtitle == sub && existing.appLabel == appLabel &&
            (artFile == null || artFile == existing.artFile) &&
            nowMs - existing.updatedAtMs in 0 until WRITE_THROTTLE_MS
        ) {
            return Upsert(list, changed = false, evicted = emptyList())
        }
        val entry = HistoryEntry(
            packageName = packageName,
            appLabel = appLabel,
            title = title,
            subtitle = sub,
            artFile = artFile ?: existing?.artFile,
            updatedAtMs = nowMs,
        )
        val rest = list.filterNot { it.sameItem(packageName, title) }
        val all = listOf(entry) + rest
        val kept = dedupe(all).take(MAX_ENTRIES)
        return Upsert(kept, changed = true, evicted = evictedArt(list, kept))
    }

    /** Removes (packageName, title); returns the new list and the removed entry (or null). */
    fun remove(list: List<HistoryEntry>, packageName: String, title: String): Pair<List<HistoryEntry>, HistoryEntry?> {
        val gone = list.firstOrNull { it.sameItem(packageName, title) } ?: return list to null
        return list.filterNot { it.sameItem(packageName, title) } to gone
    }

    /** First occurrence of each (packageName, title) wins. */
    fun dedupe(list: List<HistoryEntry>): List<HistoryEntry> = list.distinctBy { it.packageName to it.title }

    /** Entries of [before] no longer in [after] whose art file is not still referenced. */
    fun evictedArt(before: List<HistoryEntry>, after: List<HistoryEntry>): List<HistoryEntry> {
        val keptKeys = after.map { it.packageName to it.title }.toSet()
        val keptArt = after.mapNotNull { it.artFile }.toSet()
        return before.filter { (it.packageName to it.title) !in keptKeys && it.artFile != null && it.artFile !in keptArt }
    }

    /** Art files on disk that no entry references (cleanup after a crash or corrupt prefs). */
    fun orphanArt(entries: List<HistoryEntry>, filesOnDisk: List<String>): List<String> {
        val used = entries.mapNotNull { it.artFile }.toSet()
        return filesOnDisk.filter { it !in used }
    }

    /** Stable file name for an item's art: 16 hex chars of SHA-256(pkg + NUL + title) + ".png". */
    fun artFileName(packageName: String, title: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("$packageName\u0000$title".toByteArray(Charsets.UTF_8))
        return d.take(8).joinToString("") { "%02x".format(it) } + ".png"
    }

    private val serializer = ListSerializer(HistoryEntry.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<HistoryEntry>): String = json.encodeToString(serializer, list)

    /** Corrupt or missing JSON decodes to an empty list; never throws. */
    fun decode(text: String?): List<HistoryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            dedupe(json.decodeFromString(serializer, text).filter { it.title.isNotBlank() }).take(MAX_ENTRIES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** "Just now", "5 min ago", "2 h ago", "3 d ago" (English, like the rest of the UI). */
    fun relativeAge(nowMs: Long, thenMs: Long): String {
        val mins = ((nowMs - thenMs).coerceAtLeast(0)) / 60_000L
        return when {
            mins < 1 -> "Just now"
            mins < 60 -> "$mins min ago"
            mins < 24 * 60 -> "${mins / 60} h ago"
            else -> "${mins / (24 * 60)} d ago"
        }
    }
}
