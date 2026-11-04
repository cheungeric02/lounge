package ca.cheungeric.lounge.ui.patchnotes

/** Integer build number. Single source of truth: Gradle reads this line for versionCode/versionName. */
const val APP_VERSION = 1

data class PatchNote(
    val v: Int,
    val date: String,
    val title: String,
    val notes: List<String>,
)

/** Newest first. Add an entry every release and bump [APP_VERSION]. */
val PATCH_NOTES = listOf(
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
