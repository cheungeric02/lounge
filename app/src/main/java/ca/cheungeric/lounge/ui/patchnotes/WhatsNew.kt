package ca.cheungeric.lounge.ui.patchnotes

import android.content.Context

/** Pure "What's New" gate (docs/SPEC.md "Versioning + Patch Notes"). [notes] is newest first. */
object WhatsNewGate {
    fun decide(freshInstall: Boolean, lastSeen: Int?, current: Int, notes: List<PatchNote>): List<PatchNote> = when {
        // Fresh install: record silently, no popup.
        freshInstall -> emptyList()
        // Upgraded from a build that predates the gate: show the latest entry only.
        lastSeen == null -> notes.take(1)
        // Upgraded: show every entry newer than what was last seen.
        lastSeen < current -> notes.filter { it.v > lastSeen }
        // Same build (or a downgrade): nothing.
        else -> emptyList()
    }
}

/**
 * SharedPreferences "lounge_prefs" holding `lounge_lastSeenVer`. All access is fail-silent.
 *
 * T4: call [captureFreshInstall] FIRST THING in `MainActivity.onCreate` (before anything writes to
 * "lounge_prefs"), keep the result, then after the first frame call [checkWhatsNew] with it.
 */
class WhatsNewStore(context: Context) {
    private val app = context.applicationContext

    private val prefs
        get() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * True when "lounge_prefs" has no keys at all, i.e. nothing has ever been recorded. The first call
     * in the process decides; later calls (even from other instances) return the same answer, so it is
     * safe to call again after [markSeen] has written.
     */
    fun captureFreshInstall(): Boolean {
        captured?.let { return it }
        synchronized(lock) {
            captured?.let { return it }
            val fresh = try {
                prefs.all.isEmpty()
            } catch (_: Exception) {
                false
            }
            captured = fresh
            return fresh
        }
    }

    fun lastSeen(): Int? = try {
        val p = prefs
        if (p.contains(KEY_LAST_SEEN)) p.getInt(KEY_LAST_SEEN, 0) else null
    } catch (_: Exception) {
        null
    }

    fun markSeen(v: Int) {
        try {
            prefs.edit().putInt(KEY_LAST_SEEN, v).apply()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val PREFS = "lounge_prefs"
        const val KEY_LAST_SEEN = "lounge_lastSeenVer"

        private val lock = Any()
        @Volatile private var captured: Boolean? = null
    }
}

/**
 * Decides what to show AND records [APP_VERSION] as seen in one call (SPEC: write lastSeen the moment
 * the dialog is shown). Call once at boot after the first frame; show [WhatsNewDialog] if non-empty.
 */
fun checkWhatsNew(
    store: WhatsNewStore,
    freshInstall: Boolean,
    current: Int = APP_VERSION,
    notes: List<PatchNote> = PATCH_NOTES,
): List<PatchNote> {
    val lastSeen = store.lastSeen()
    val entries = WhatsNewGate.decide(freshInstall, lastSeen, current, notes)
    if (freshInstall || entries.isNotEmpty() || lastSeen == null) store.markSeen(current)
    return entries
}
