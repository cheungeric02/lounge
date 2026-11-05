package ca.cheungeric.lounge.remote

/**
 * v4 "auto-pick": after the phone launches YouTube with a profile name, find that profile on
 * YouTube's "Who's watching?" screen and return the node to click. Pure (works on a flat
 * snapshot of the accessibility tree) so it can be unit-tested.
 */
object ProfilePicker {

    const val YOUTUBE = "com.google.android.youtube.tv"

    /** Packages that get an auto-pick when launched with a profile. */
    val SUPPORTED = setOf(YOUTUBE)

    /** How long after the launch we keep looking for the picker screen. */
    const val WINDOW_MS = 25_000L
    const val POLL_MS = 600L
    const val MAX_NODES = 1500
    const val MAX_PROFILE = 40

    /** Text that only appears on an account picker; we never click unless one is on screen. */
    private val PICKER_HINTS = listOf("who's watching", "whos watching", "choose an account", "add account", "select an account")

    /** One node of the snapshot. [parent] is the index of the parent node, -1 for the root. */
    data class Node(
        val text: String?,
        val desc: String?,
        val clickable: Boolean,
        val parent: Int,
    )

    /** Trimmed 1..[MAX_PROFILE] chars, else null. */
    fun normalizeProfile(raw: String?): String? =
        raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() && it.length <= MAX_PROFILE }

    private fun norm(s: String?): String =
        (s ?: "").lowercase().replace('’', '\'').replace('‘', '\'').replace(Regex("\\s+"), " ").trim()

    /** "Eric", "Eric Cheung", "Eric, eric@gmail.com", "Eric (signed in)" all match "eric". */
    fun matches(label: String?, profile: String): Boolean {
        val l = norm(label)
        val p = norm(profile)
        if (l.isEmpty() || p.isEmpty()) return false
        if (l == p) return true
        if (!l.startsWith(p)) return false
        val next = l[p.length]
        return next == ' ' || next == ',' || next == '(' || next == '.' || next == '-'
    }

    fun isPickerScreen(nodes: List<Node>): Boolean = nodes.any { n ->
        val t = norm(n.text) + " " + norm(n.desc)
        PICKER_HINTS.any { h -> t.contains(h) || t.replace("'", "").contains(h) }
    }

    /**
     * Index of the node to click for [profile], or null when this isn't the picker screen or the
     * profile isn't on it. Prefers the nearest clickable ancestor-or-self of the first match.
     */
    fun target(nodes: List<Node>, profile: String): Int? {
        if (!isPickerScreen(nodes)) return null
        val match = nodes.indexOfFirst { matches(it.text, profile) || matches(it.desc, profile) }
        if (match < 0) return null
        var i = match
        var guard = 0
        while (i >= 0 && guard++ < 64) {
            if (nodes[i].clickable) return i
            i = nodes[i].parent
        }
        return match
    }
}
