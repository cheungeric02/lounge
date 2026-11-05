package ca.cheungeric.lounge.ui.patchnotes

/** Integer build number. Single source of truth: Gradle reads this line for versionCode/versionName. */
const val APP_VERSION = 4

data class PatchNote(
    val v: Int,
    val date: String,
    val title: String,
    val notes: List<String>,
)

/** Newest first. Add an entry every release and bump [APP_VERSION]. */
val PATCH_NOTES = listOf(
    PatchNote(
        4,
        "2026-11-04",
        "YouTube profile auto-pick",
        listOf(
            "Opening YouTube from a phone now picks that phone's profile on the \"Who's watching?\" screen automatically. Set it on each phone in Lounge Remote → ⋮ → This phone.",
            "Needs the Home button accessibility service on, and both accounts added in YouTube.",
        ),
    ),
    PatchNote(
        3,
        "2026-11-04",
        "Now Playing, volume and typing",
        listOf(
            "Now Playing: see what's playing on the Home screen and on your phone, with play/pause, skip and seek from the phone.",
            "Phone remote: volume up/down/mute and a sleep timer that pauses playback and turns the screen off.",
            "Share to TV: share a YouTube, Netflix, Spotify, Prime or Disney+ link from your phone and it opens in the right TV app.",
            "Type from your phone into TV search boxes (apps that draw their own keyboard, like YouTube and Netflix, don't support it).",
            "Setup: turn on Settings → Phone remote → Now Playing access. If the Home button stops working after this update, turn the Lounge accessibility service off and on again.",
        ),
    ),
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
