package ca.cheungeric.lounge.ui.patchnotes

/** Integer build number. Single source of truth: Gradle reads this line for versionCode/versionName. */
const val APP_VERSION = 7

data class PatchNote(
    val v: Int,
    val date: String,
    val title: String,
    val notes: List<String>,
)

/** Newest first. Add an entry every release and bump [APP_VERSION]. */
val PATCH_NOTES = listOf(
    PatchNote(
        7,
        "2026-11-08",
        "Home from your phone switches the TV",
        listOf(
            "Pressing Home in Lounge Remote now also switches the TV to the HDMI port this box is on, then opens Lounge. Choose the port in Settings → TV power control (default HDMI 2).",
        ),
    ),
    PatchNote(
        6,
        "2026-11-05",
        "A calmer Home",
        listOf(
            "Minimal Home: a big clock, the date and weather over a James Webb photo, with your favourites in one row. Pick the photo and how dim it is in Settings → Background.",
            "Press Down for your TV's HDMI inputs (see what's connected and switch with OK) and what you've recently watched.",
            "The All apps tile at the end of the row opens every app in a full-screen grid. Back closes it.",
            "A Continue card picks up the last thing you watched. Hold OK on it to remove it from history.",
            "Colours follow the app you're on: the focus ring and a soft glow take on its colour.",
            "The ambient screen now slowly drifts across a dimmed Webb photo. Black is still available in Settings → Ambient screen.",
        ),
    ),
    PatchNote(
        5,
        "2026-11-05",
        "TV power, phone remote and ambient screen",
        listOf(
            "Turn the TV on and off and switch HDMI inputs from your phone. Set it up in Settings → TV power control.",
            "Lounge Remote has a new Remote tab with a D-pad, Back, Home and volume (D-pad needs Android 13+).",
            "Ambient screen: after a few idle minutes on Home, the screen goes pure black with a dim clock and weather. Pick Off/5/10/30 min in Settings. Also available as the \"Lounge Ambient\" screensaver.",
        ),
    ),
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
