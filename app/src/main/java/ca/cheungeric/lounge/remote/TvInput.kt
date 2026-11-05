package ca.cheungeric.lounge.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The focused editable field (SPEC "v3 → tv/input"). Pure snapshot + builder; [TvInput.state] is the
 * process-wide hand-off from HomeButtonService (writer) to RemoteBridge (publisher).
 */
data class InputSnapshot(val active: Boolean, val pkg: String = "", val hint: String = "", val text: String = "") {
    companion object {
        val NONE = InputSnapshot(active = false)
    }
}

object TvInput {

    /** Longest text published / accepted by the `text` command. */
    const val MAX_TEXT = 500

    private val _state = MutableStateFlow(InputSnapshot.NONE)
    val state: StateFlow<InputSnapshot> = _state.asStateFlow()

    fun set(s: InputSnapshot) {
        _state.value = s
    }

    /**
     * Snapshot for the focused node. Only an editable, focused, non-password node is ever read;
     * anything else is [InputSnapshot.NONE] (text of other views is never captured).
     * [showingHint] = the node's text is really its hint (empty field).
     */
    fun fromNode(
        present: Boolean,
        editable: Boolean,
        focused: Boolean,
        password: Boolean,
        pkg: CharSequence?,
        hint: CharSequence?,
        text: CharSequence?,
        showingHint: Boolean,
    ): InputSnapshot {
        if (!present || !editable || !focused || password) return InputSnapshot.NONE
        val h = hint?.toString().orEmpty().take(200)
        val t = if (showingHint) "" else text?.toString().orEmpty()
        return InputSnapshot(true, pkg?.toString().orEmpty(), h, t.take(MAX_TEXT))
    }

    /** `tv/input`. Inactive → `{active:false}` with empty fields. */
    fun payload(s: InputSnapshot): String = buildJsonObject {
        put("active", s.active)
        put("pkg", if (s.active) s.pkg else "")
        put("hint", if (s.active) s.hint else "")
        put("text", if (s.active) s.text else "")
    }.toString()
}
