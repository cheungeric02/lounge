package ca.cheungeric.lounge.ui.background

import android.content.Context
import android.content.SharedPreferences
import ca.cheungeric.lounge.ui.patchnotes.WhatsNewStore

/** Home/ambient background photo (SPEC "v6 → Background"). [key] is the stored value. */
enum class BgImage(val key: String) {
    Cliffs("cliffs"),
    Pillars("pillars"),
    Deep("deep"),
    Black("black"),
}

/** Screensaver look: drifting Webb photo or the original pure-black clock. */
enum class AmbientStyle(val key: String) {
    Webb("webb"),
    Black("black"),
}

/** Resolved background settings; [dim] is the scrim strength in percent (30, 50 or 70). */
data class BackgroundSettings(
    val image: BgImage = BgImage.Cliffs,
    val dim: Int = BackgroundPrefs.DEFAULT_DIM,
    val ambientStyle: AmbientStyle = AmbientStyle.Webb,
) {
    /** The screensaver shows the photo only when its style is Webb and a photo is picked. */
    val ambientPhoto: BgImage? get() = image.takeIf { ambientStyle == AmbientStyle.Webb && it != BgImage.Black }
}

/**
 * Pure parsing of the background keys in `lounge_prefs`; unknown or missing values fall back to
 * defaults. [read]/[write] are the only Android-touching helpers and never throw.
 */
object BackgroundPrefs {
    const val KEY_IMAGE = "lounge_bgImage"
    const val KEY_DIM = "lounge_bgDim"
    const val KEY_AMBIENT_STYLE = "lounge_ambientStyle"

    const val DEFAULT_DIM = 50
    val DIM_LEVELS = listOf(30, 50, 70)

    fun imageOf(value: Any?): BgImage = BgImage.entries.firstOrNull { it.key == value } ?: BgImage.Cliffs

    fun dimOf(value: Any?): Int {
        val n = when (value) {
            is Int -> value
            is Long -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        return n?.takeIf { it in DIM_LEVELS } ?: DEFAULT_DIM
    }

    fun ambientStyleOf(value: Any?): AmbientStyle =
        AmbientStyle.entries.firstOrNull { it.key == value } ?: AmbientStyle.Webb

    /** Builds settings from a raw prefs map (`SharedPreferences.getAll()`). */
    fun fromMap(map: Map<String, *>): BackgroundSettings = BackgroundSettings(
        image = imageOf(map[KEY_IMAGE]),
        dim = dimOf(map[KEY_DIM]),
        ambientStyle = ambientStyleOf(map[KEY_AMBIENT_STYLE]),
    )

    fun read(context: Context): BackgroundSettings = try {
        fromMap(prefs(context).all)
    } catch (_: Throwable) {
        BackgroundSettings()
    }

    fun write(context: Context, s: BackgroundSettings) {
        try {
            prefs(context).edit()
                .putString(KEY_IMAGE, s.image.key)
                .putInt(KEY_DIM, dimOf(s.dim))
                .putString(KEY_AMBIENT_STYLE, s.ambientStyle.key)
                .apply()
        } catch (_: Throwable) {
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(WhatsNewStore.PREFS, Context.MODE_PRIVATE)
}
