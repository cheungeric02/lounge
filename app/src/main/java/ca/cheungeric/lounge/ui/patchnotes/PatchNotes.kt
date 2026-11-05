package ca.cheungeric.lounge.ui.patchnotes

/** Integer build number. Single source of truth: Gradle reads this line for versionCode/versionName. */
const val APP_VERSION = 2

data class PatchNote(
    val v: Int,
    val date: String,
    val title: String,
    val notes: List<String>,
)

/** Newest first. Add an entry every release and bump [APP_VERSION]. */
val PATCH_NOTES = listOf(
    PatchNote(
        2,
        "2026-11-04",
        "Phone remote",
        listOf(
            "Phone remote: control Lounge from your phone — open apps, reorder favorites, hide apps, Home and Back. Turn it on in Settings → Phone remote and scan the QR code.",
            "Home button setup now shows the steps on screen if the shortcut can't open Accessibility settings.",
        ),
    ),
    PatchNote(
        1,
        "2026-11-04",
        "Lounge is here",
        listOf(
            "A clean home screen with clock, date and Toronto weather",
            "Favorites and all-apps rows you can reorder, favorite or hide",
            "The Home button brings you back to Lounge",
            "Self-updates from GitHub Pages",
        ),
    ),
)
