# Lounge — spec

A clean, ad-free custom home screen (launcher) for Eric's Android TV devices. Adult, personal use — no kid/parental features.

## Goals (MVP)

1. Replace the stock Google TV / Sony home screen with Lounge: clock, date, Toronto weather, favorites row, all-apps row.
2. Pressing Home on the remote lands on Lounge (no ADB needed after first install).
3. Favorite / reorder / hide apps from the TV with the remote (hold OK).
4. Self-updating: the TV checks GitHub Pages for a newer APK and installs it.
5. Patch Notes (Settings) + one-time What's New popup, Eric's standard convention (below).

## Non-goals

- No parental controls, time limits, kid profiles, PINs.
- No debrid, torrent, scraping or third-party add-on stream finding/playback — ever. Stremio is only launched/deep-linked as an installed app.
- No ADB-to-self (dadb), no debloat UI in MVP (debloat lives in `C:\Users\eric.cheung\Claude\tv\bravia-debloat.md`).
- No phone companion app, Trakt, Hue, or other integrations in MVP (phase 2, see bottom).
- Do not reuse code from leafsfan/KerbyTV (private, unlicensed). Build from scratch.

## Devices

| Device | IP | Android | Notes |
|---|---|---|---|
| Google TV box (primary target) | 192.168.2.21 | 14 (API 34) | Google TV Streamer-class |
| Sony Bravia "Media Room TV" | 192.168.2.32 | 9 (API 28) | must also install → minSdk 28 |

## Stack

- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.11.1 (wrapper), JDK 17 (Temurin, per-user install)
- compileSdk 35, targetSdk 34, minSdk 28. No native code, no `abiFilters`.
- Jetpack Compose (BOM 2024.12.01) + `androidx.tv:tv-material:1.0.0` + `androidx.tv:tv-foundation` not needed (use Compose foundation lazy rows).
- DataStore Preferences 1.1.1 for settings; kotlinx-serialization-json 1.7.3 for JSON; `HttpURLConnection` (no OkHttp/Retrofit) for the two HTTP calls.
- Tests: JUnit 4 unit tests for pure logic (ordering, patch-note gate, version compare, weather parsing).
- Screenshots (v6): Paparazzi 1.3.5 Gradle plugin, test-only (JVM layoutlib, no emulator needed on this PC). `HomeScreenshots` renders the v6 screens with sample data; `./gradlew recordPaparazziDebug` writes PNGs to `app/src/test/snapshots/`. Never part of the APK.
- Single module `:app`, package `ca.cheungeric.lounge`.

No admin rights on the dev PC: JDK and Android SDK cmdline-tools are installed per-user under `%LOCALAPPDATA%\Programs` (JDK) and `%LOCALAPPDATA%\Android\Sdk`. `local.properties` points `sdk.dir` there (gitignored).

## Architecture

```
ca.cheungeric.lounge
  MainActivity            HOME + LEANBACK_LAUNCHER activity, hosts Compose, runs boot checks
  LoungeApp               Application; builds the AppContainer (manual DI, no Hilt)
  data/
    AppEntry              model: packageName, label, banner/icon Drawable, launchIntent
    AppsRepository        queries PackageManager, emits List<AppEntry>, refreshes on package add/remove
    LayoutPrefs           DataStore: favorites (ordered List<String>), hidden (Set<String>), allAppsOrder
    HomeLayout            pure fun: (installed, prefs) -> HomeState(favorites, others)  [unit-tested]
    WeatherRepository     Open-Meteo client, caches last result, refresh every 30 min
  ui/
    theme/                M3-Expressive-flavoured dark TV theme (tokens below)
    home/                 HomeScreen, ClockHeader, WeatherCard, AppRow, AppTile, ContinueWatchingPlaceholder
    edit/                 Tile action sheet (move left/right, favorite/unfavorite, hide)
    settings/             SettingsScreen (hidden apps, home-button setup, updates, patch notes, about)
    patchnotes/           PatchNotes.kt (APP_VERSION, PATCH_NOTES), PatchNoteEntry composable, WhatsNewDialog, gate logic
  home/
    HomeButtonService     AccessibilityService that brings Lounge forward when Home is pressed
  update/
    UpdateChecker         fetch version.json, compare versionCode
    UpdateInstaller       download APK, install via PackageInstaller session
```

Navigation: a single activity with a tiny in-memory screen state (`Home` | `Settings`), Back from Settings → Home. Back on Home does nothing (it's the launcher).

## Home screen (approved mockup, 2026-11-04)

Top to bottom, 10-foot UI, 1920×1080 dp-scaled, overscan-safe padding 48dp horizontal / 27dp vertical:
1. **Header**: left — clock (72sp, weight Medium, 12-hour, small "am/pm" suffix 28sp in secondary color), date below ("Wednesday, November 4", 20sp secondary). Right — weather pill (cloud/sun icon from condition, temp °C 26sp, "Toronto · H 7° L 1°" 15sp secondary) and a round settings button (focusable).
2. **Continue watching** — label "Continue watching" + 3 dashed placeholder cards. MVP: hidden by default; shown only when a debug/dev flag is on. (Phase 2: Trakt.)
3. **Favorites** — label + LazyRow of large banner tiles (16:9, 220×124dp), 20dp corner radius. Focused tile scales 1.1× with a 3dp light outline (offset 3dp) — the M3 Expressive "spring" focus (spring dampingRatio 0.6).
4. **All apps** — label + LazyRow of smaller tiles (160×90dp, 16dp radius), every installed launchable app not in favorites and not hidden, alphabetical unless reordered.
5. Footer hint (13sp tertiary): "Hold OK on any tile to move, favorite or hide it".

Tiles show the app's TV banner (`ActivityInfo.loadBanner` → `ApplicationInfo.loadBanner`), else the icon centered on a tonal surface with the label beneath. Click (OK) launches the app. Long-press OK opens the tile action sheet.

Default favorites on first run, only those installed, in this order: Stremio (`com.stremio.one`), YouTube (`com.google.android.youtube.tv`), Netflix (`com.netflix.ninja`), Disney+ (`com.disney.disneyplus`), Prime Video (`com.amazon.amazonvideo.livingroom`), Crunchyroll (`com.crunchyroll.crunchyroid`). Lounge itself never appears in the rows.

### Tile action sheet
Compose dialog listing: "Move left", "Move right" (within its row), "Add to favorites" / "Remove from favorites", "Hide", "App info" (opens system app details), "Cancel". Hidden apps are restored in Settings → Hidden apps.

### Theme tokens (dark only)
background `#14121A`, surface `#1E1A24`, surfaceVariant `#2A2433`, primary `#D9C2FF`, primaryContainer `#6B4FA8`, onSurface `#E8E3EF`, onSurfaceVariant `#B9AFCB`, outline `#4A4256`, tertiary text `#8E849E`, focus ring `#F2EAFF`. Shapes: large 20dp, medium 16dp, pill 50%. Typography: default sans, weights 400/500 only.

## Weather

Open-Meteo, no key: `https://api.open-meteo.com/v1/forecast?latitude=43.6532&longitude=-79.3832&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=America%2FToronto&forecast_days=1`. Map WMO `weather_code` → icon + short label. Refresh on start and every 30 min while foreground; on failure keep the last cached value (DataStore), hide the pill if none.

## Home button

- `MainActivity` declares `MAIN` + `HOME` + `DEFAULT` and `MAIN` + `LEANBACK_LAUNCHER` intent filters, `launchMode="singleTask"`.
- On Google TV the stock launcher can't be replaced via settings, so `HomeButtonService` (AccessibilityService, `canRequestFilterKeyEvents`) intercepts `KEYCODE_HOME` key-up and starts `MainActivity` (consumes the event). Fallback: on `TYPE_WINDOW_STATE_CHANGED` where the package is a known stock launcher (`com.google.android.apps.tv.launcherx`, `com.google.android.tvlauncher`, `com.google.android.leanbacklauncher`) within 1.5 s of a Home press, start Lounge.
- Settings → "Home button" shows whether the service is enabled and a button that opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Onboarding tip on Home if not enabled (dismissible).

## Self-update + publishing

- Releases are published to GitHub Pages of the public repo `cheungeric02/lounge`: `https://cheungeric02.github.io/lounge/version.json` and `.../lounge.apk`, plus a plain `index.html` download page.
- `version.json`: `{ "versionCode": 3, "versionName": "v3", "apkUrl": "https://cheungeric02.github.io/lounge/lounge.apk", "sha256": "..." }`.
- On start (and once a day), `UpdateChecker` fetches version.json; if `versionCode > BuildConfig.VERSION_CODE`, show a non-blocking banner "Update available — v3" with "Install". Install downloads to cache dir, verifies sha256, installs via `PackageInstaller` session (needs `REQUEST_INSTALL_PACKAGES`; first time Android asks Eric to allow "Install unknown apps" for Lounge). Settings → "Check for updates".
- CI (GitHub Actions, `.github/workflows/release.yml`): on push to `main` → JDK 17, build `assembleRelease` signed with the release keystore from secrets (`LOUNGE_KEYSTORE_B64`, `LOUNGE_KEYSTORE_PASSWORD`, `LOUNGE_KEY_ALIAS`, `LOUNGE_KEY_PASSWORD`), run unit tests, generate version.json, deploy to Pages via `actions/deploy-pages`. PRs only build + test.
- Release keystore lives outside the repo at `%USERPROFILE%\.lounge-keys\lounge-release.jks` (password in `%USERPROFILE%\.lounge-keys\keystore.properties`). Local release builds read `keystore.properties` if present; otherwise release is unsigned. Debug builds use the debug key (a debug build can't update over the release build — uninstall first).

## Versioning + Patch Notes (Eric's convention, adapted for Android)

- `APP_VERSION` = integer build number, the single source of truth in `ui/patchnotes/PatchNotes.kt`. Gradle reads it (regex on that file) to set `versionCode = APP_VERSION` and `versionName = "v$APP_VERSION"`. Bump it every release.
- `PATCH_NOTES: List<PatchNote(v: Int, date: "YYYY-MM-DD", title, notes: List<String>)>`, newest first.
- ONE shared composable `PatchNoteEntry(entry, isCurrent)` renders both the Settings "Patch Notes" list (all entries, current highlighted, shows `vN` near heading) and the "What's New" dialog.
- Gate `checkWhatsNew(freshInstall)` once at boot after the first frame, using SharedPreferences key `lounge_lastSeenVer`:
  - fresh install (no `lounge_*` prefs existed at boot, captured before anything writes) → record APP_VERSION silently, no popup;
  - lastSeen missing but not fresh → show the latest entry only;
  - lastSeen < APP_VERSION → show the delta (entries with v > lastSeen);
  - else nothing. Write lastSeen the moment the dialog is shown. All prefs access fail-silent.
- v1 entry: "Lounge is here" — home screen, weather, favorites, home button, self-update.

## First install (Eric, once per device)

Google TV box: install "Downloader" (AFTVnews) from Play Store → enter `cheungeric02.github.io/lounge` → download → allow install → open Lounge → Settings → Home button → enable "Lounge home button" in Accessibility. Bravia: same, or `adb install` via Termux.

## Definition of done (MVP)

- `gradlew assembleDebug testDebugUnitTest lintDebug` passes locally; CI green on `main`; Pages serves version.json + lounge.apk.
- Home renders per mockup; favorites/all-apps populated from real packages; hold-OK sheet works; prefs persist.
- Weather shows Toronto; survives offline.
- Home button returns to Lounge on the Google TV box (Eric verifies on device).
- Update banner appears when Pages has a higher versionCode; install works.
- What's New/Patch Notes behave per the gate rules.

## Phase 2 (not now)

Trakt "Continue watching" (needs a small server for the client secret); Stremio deep links into titles; Hue scenes ("Movie night"); Dailies to-dos, bench progress, Japanese word of the day, Biff's Cup scores; Bravia IP-control buttons.

## v2 — Phone remote (Lounge Remote)

A phone web app that mirrors the TV's rows and can launch / favorite / reorder / hide apps on the TV, plus Home and Back. The TV stays the single owner of its layout: the phone only sends **commands**; the TV applies them to its own DataStore and republishes. No merge conflicts possible.

### Hosting
- Phone app: own repo `cheungeric02/lounge-remote` (single self-contained `index.html` + `manifest.json` + `sw.js` + icons, set up like Nest/Basecamp: relative start_url/scope `./`, no id) → `https://cheungeric02.github.io/lounge-remote/`. Moved there in web v7 after Android installs kept failing at `/lounge/remote/` (phantom "already installed"). This repo's CI keeps `/lounge/remote/` and `/lounge/app/` as redirects (hash preserved), so the pairing URL below still works.
- Built per Eric's web-app process: viewport meta, Material 3 Expressive (purple primary tuned to the TV palette: dark primary `#D9C2FF`, light primary `#6B4FA8`), light/dark toggle (☾/☀, `data-theme` + localStorage + anti-flash head script), Android/Fold-first (works folded ~360px and unfolded ~700–900px), web patch-notes convention (`APP_VERSION` = sw cache number `lounge-remote-vN`, `PATCH_NOTES`, key `lounge_remote_lastSeenVer`, fresh install silent, missing → latest, older → delta).
- Firebase JS SDK 10.12.5 (modular, from gstatic) on Eric's shared **bon-echo** RTDB. Web config (public, not secret): apiKey `AIzaSyD9AJbPC9M4gpTyvfIoRcqILq0ZOom1OBs`, authDomain `bon-echo-bd946.firebaseapp.com`, databaseURL `https://bon-echo-bd946-default-rtdb.firebaseio.com`, projectId `bon-echo-bd946`, appId `1:196437731482:web:3ac836962ce7d89e735723`. Rules are wide open: the pairing key is the only gate (same posture as his other apps). Firebase only works on https (Pages), not file://.

### Pairing key
- Generated on the TV: 4 words from a fixed 256-word lowercase a–z list (`remote/words.txt`, one per line, identical list embedded in Kotlin and JS), joined by `-`, e.g. `maple-otter-river-cloud` (32 bits; obscurity, not auth). Stored in `lounge_prefs` key `lounge_remoteKey`. Settings can regenerate it (TV deletes the old `lounge/<oldKey>` node first).
- Pairing URL: `https://cheungeric02.github.io/lounge/remote/#k=<key>` (fragment, never a query string). TV Settings shows it as a QR code + the key in large text. Phone stores the key in `localStorage["lounge_remote_key"]`, then `history.replaceState` strips the fragment. Phone can also type the key (or paste a full pairing URL), or Unpair.

### Data contract (`lounge/<key>/…`)
Package names are not valid RTDB keys → `pkgKey = pkg.replace('.', ',')`.
- `tv/status` — `{ appVersion: Int, homeService: Boolean, lastSeen: <server timestamp> }`. TV writes it on bridge start and every 60 s (`{".sv":"timestamp"}`). Phone shows **Online** when `serverNow - lastSeen < 150 s` (phone uses `.info/serverTimeOffset`).
- `tv/apps` — object `pkgKey → { pkg, label, iconHash }`, every launchable app incl. hidden ones, written whole (PUT) when the installed list or labels change.
- `tv/layout` — `{ favorites: [pkg…], others: [pkg…], hidden: [pkg…] }` exactly as the TV's HomeLayout builds it (favorites order, visible all-apps order, hidden alphabetical by label). Written whole whenever it changes. **Readers must normalize missing arrays to `[]`** (RTDB drops empty arrays).
- `tv/icons/<pkgKey>` — `"data:image/png;base64,…"`: the TV banner scaled to 192×108, else the icon scaled to 96×96. Upload only when `iconHash` (sha1 hex of the PNG bytes) changed; delete icons of uninstalled apps.
- `cmd/<pushId>` — written by the phone with `push()`: `{ type, pkg?, to?, at: {".sv":"timestamp"} }`. Types: `launch`, `favorite`, `unfavorite`, `hide`, `unhide`, `moveFavorite` (`pkg`, `to` = target index in favorites, clamped), `home`, `back`.
- TV command handling: for each command child, DELETE it first, then apply only if valid and fresh (`|tvServerNow - at| <= 30 s`; TV estimates server time from the HTTP `Date` header offset; commands with a missing/non-numeric `at` are dropped). Unknown types and packages that aren't installed are ignored. `home` = bring Lounge forward; `back` = `performGlobalAction(GLOBAL_ACTION_BACK)` (only when HomeButtonService is running).

### TV side
- `remote/` package: `WordList`, `RemoteKey` (generate/validate), `SseParser` (pure Firebase REST streaming parser: `event:`/`data:` lines → put/patch/keep-alive/cancel/auth_revoked), `FirebaseRest` (HttpURLConnection GET-stream with `Accept: text/event-stream` / PUT / PATCH / DELETE on `<db>/<path>.json`, Date-header clock offset), `RemoteCommand` (parse + validate + freshness, pure), `RemoteBridge` (publish status/apps/layout/icons, consume commands, reconnect with backoff 2 s → 60 s, never throws).
- Process-wide `RemoteBridge` owned by `AppContainer`, ref-counted: started when HomeButtonService is connected and when MainActivity is started; runs only while Settings → Phone remote is **on** (pref `lounge_remoteEnabled`, default off; key generated on first enable). Background launches work because HomeButtonService is a system-bound accessibility service; without the service, phone launches only work while Lounge is in front (Settings says so).
- Layout edits go through the same `HomeLayout` transforms + `LayoutPrefs` as the TV sheet; new pure `HomeLayout.moveFavoriteTo(prefs, pkg, index)`.
- Settings → "Phone remote" section: On/Off, QR code (pure-Kotlin QR encoder, byte mode, ECC M, versions 1–6, unit-tested) + URL + key in large text, "New key", status line (Connected / Connecting / Offline / Off).

### Phone app (Lounge Remote)
- Unpaired: welcome card, "Pairing key" field (accepts the 4-word key or a full pairing URL), hint "On the TV: Lounge → Settings → Phone remote, then scan the QR code".
- Paired: top bar (title "Lounge", online/offline chip, theme toggle, ⋮ menu: Patch notes, Unpair). Remote strip: Home + Back pill buttons. **Favorites**: reorderable list (drag handle, pointer events, works with touch), tap row = launch, overflow = Remove from favorites / Hide. **All apps**: search field + icon grid (tap = launch, overflow/long-press = Add to favorites / Hide). **Hidden** (collapsed section): Unhide. Snackbar "Opening Netflix on TV" / "TV is offline" when offline.
- No optimistic layout: lists update when the TV republishes `tv/layout` (typically < 1 s); a pending command shows a subtle spinner on its row for up to 5 s.

## v3 — Now Playing, Share to TV, volume + sleep, phone typing

Same posture as v2: the phone only sends `cmd/*`, the TV publishes `tv/*`. All new TV behaviour lives in the existing `remote/` bridge plus two OS hooks.

### TV hooks
- **Media access**: new `NotificationListenerService` `home/MediaAccessService` (manifest-declared, `BIND_NOTIFICATION_LISTENER_SERVICE`, does nothing with notifications). It only exists so `MediaSessionManager.getActiveSessions(ComponentName)` is allowed. The user grants it once (Settings → Phone remote → "Now Playing access", same component-fallback pattern as `HomeServiceStatus`: try `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, then TV-settings components, then `ACTION_SETTINGS`; always show manual steps). Without it, Now Playing/transport are off and the phone says so.
- **Typing**: `home_button_service.xml` gains `typeViewFocused|typeViewTextChanged|typeWindowStateChanged`, `canRetrieveWindowContent="true"`, `flagRetrieveInteractiveWindows`. Only editable, focused nodes are read; never read or publish non-editable content, and never publish password fields (`isPassword` → `active:false`).

### Data contract additions (`lounge/<key>/…`)
- `tv/status` gains `mediaAccess: Boolean`, `typing: Boolean` (HomeButtonService running).
- `tv/nowPlaying` — `{ state: "playing"|"paused"|"none", pkg, app, title, subtitle, durationMs, positionMs, positionAt, speed, artHash, canSeek, canSkip }`. `positionAt` is estimated server time (ms) when `positionMs` was sampled. Chosen session: first `STATE_PLAYING`, else most recent paused, else `state:"none"`. Rewritten on metadata/playback-state changes (not on every tick). Phone extrapolates position while playing.
- `tv/art` — `"data:image/jpeg;base64,…"` of the album/poster art scaled to fit 320×320, written only when `artHash` (sha1 of bytes) changes; deleted when none.
- `tv/volume` — `{ level, max, muted }` for `STREAM_MUSIC`, republished after each volume command and on change (poll every 5 s while the bridge runs is fine).
- `tv/sleep` — `{ at: <server ms> }` while a sleep timer is armed, deleted otherwise.
- `tv/input` — `{ active: Boolean, pkg, hint, text }` for the currently focused editable field; `active:false` when none.
- New `cmd` types (same freshness/delete-first rules):
  - `media` `{ action: "toggle"|"play"|"pause"|"next"|"prev"|"seek", ms? }` — `seek` is relative (`ms` = ±delta, clamped to [0, duration]).
  - `volume` `{ action: "up"|"down"|"mute" }` — `AudioManager.adjustStreamVolume(STREAM_MUSIC, …, FLAG_SHOW_UI)`; mute = `ADJUST_TOGGLE_MUTE`.
  - `sleep` `{ minutes: 0..240 }` — 0 cancels. On expiry: pause the active session, then go to Lounge Home and try to start the screensaver (`com.android.systemui/.Somnambulator`, ignore failure). Timer held by the bridge (process stays alive via the accessibility service); re-armed from `tv/sleep` on bridge restart if still in the future.
  - `open` `{ url }` — only `http`/`https`, ≤2048 chars. Pure `LinkRouter` maps URL → target: YouTube (`youtube.com`, `youtu.be`, `m.youtube.com`, `music.youtube.com`) → `ACTION_VIEW` on `com.google.android.youtube.tv` (normalise youtu.be/shorts to `https://www.youtube.com/watch?v=ID`); Netflix (`netflix.com/title|watch/ID`) → `com.netflix.ninja`; Spotify (`open.spotify.com`) → `com.spotify.tv.android`; Prime (`primevideo.com`, `amazon.*/gp/video`) → `com.amazon.amazonvideo.livingroom`; Disney+ → `com.disney.disneyplus`; anything else → plain `ACTION_VIEW` (any resolver). Each mapped target falls back to plain `ACTION_VIEW`, then to a TV toast "Can't open that link on this TV". Brings the app forward with `FLAG_ACTIVITY_NEW_TASK`.
  - `text` `{ text }` (≤500 chars) — `ACTION_SET_TEXT` on the focused editable node (whole value, phone sends the full string debounced). `submit` — `AccessibilityAction.ACTION_IME_ENTER` (API 30+), else ignored.
- Limits (tell the user): typing works in standard Android text fields (Google TV search, Settings, most apps' search boxes); apps that draw their own on-screen keyboard (YouTube, Netflix) can't receive it. Volume on the Google TV box may be routed by HDMI-CEC; if the box has no volume of its own, commands do nothing.

### Phone app v3
- **Now Playing card** at the top of the paired view (above Home/Back): art (or app icon), title, subtitle, app name, progress bar with elapsed/total, transport row (⏮ / −10 / ⏯ / +10 / ⏭, hidden pieces when `canSeek`/`canSkip` false). Collapsed "Nothing playing" line when `state:"none"`; "Turn on Now Playing access on the TV" hint when `mediaAccess` false.
- **Volume row**: − / mute / + with the level as a small bar; **Sleep** chip opens a sheet (Off, 15, 30, 60, 90 min, custom) and shows the countdown while armed.
- **Keyboard**: when `tv/input.active`, a bottom sheet slides up automatically ("Typing into <app>") with a text field prefilled from `tv/input.text`, sends `text` debounced 200 ms, Enter → `submit`. Also a ⌨ button to open it manually.
- **Send link**: ⋮ → "Send a link" sheet (paste URL). Manifest `share_target` `{ action: "/lounge/remote/", method: "GET", params: { title: "title", text: "text", url: "url" } }`; on load with those params, extract the first http(s) URL from url/text/title, send `open`, show "Opening on TV", then strip the query with `history.replaceState`.
- `?demo=1` fake TV implements all of the above. Web APP_VERSION 3 / sw `lounge-remote-v3`, patch note.

### TV Home v3
- Small Now Playing pill/card in the Home header area when something is playing or paused (art thumb, title, app, state glyph). Not focusable-critical: D-pad order unchanged.
- APP_VERSION 3 + patch note.

## v5 — Power + TV controls, phone remote page, ambient screen (TV APP_VERSION 5, web 5)

### Data contract additions (`lounge/<key>/…`)
- `tv/status` gains `keys: Boolean` (D-pad commands work: HomeButtonService running AND SDK ≥ 33) and `power: "bravia"|"sleep"|"none"` — how Power works: `bravia` = Bravia IP control configured and the last call succeeded; `sleep` = no Bravia control, but the accessibility service can put the box to sleep (HDMI-CEC then turns the TV off; turning ON is impossible from the phone); `none` = neither.
- `tv/power` — `{ state: "on"|"standby"|"unknown" }`, only while `power == "bravia"` (from Bravia `getPowerStatus`: `active` → on, `standby` → standby); republished after each power/input command and polled every 15 s while the bridge runs; deleted when Bravia control is not configured.
- New `cmd` types (same freshness/delete-first rules as v2):
  - `power` `{ action: "toggle"|"on"|"off" }`. Bravia configured → JSON-RPC `setPowerStatus` (toggle reads `getPowerStatus` first; `on` first sends a Wake-on-LAN magic packet if the Bravia MAC is known). Not configured → `off`/`toggle` = `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` (box sleeps), `on` = ignored.
  - `input` `{ port: 1..4 }` — Bravia `setPlayContent` `extInput:hdmi?port=N` (Bravia only; ignored otherwise).
  - `key` `{ key: "up"|"down"|"left"|"right"|"ok"|"back"|"home" }` — `GLOBAL_ACTION_DPAD_UP/DOWN/LEFT/RIGHT/CENTER` (API 33+), `back` = `GLOBAL_ACTION_BACK`, `home` = same as the `home` command. Key commands older than 3 s (vs estimated server time) are dropped so a laggy connection never replays a burst.

### Bravia IP control (TV side)
- Sony REST API over plain HTTP on the LAN: `POST http://<ip>/sony/system` / `/sony/avContent`, body `{"method","id","params":[…],"version":"1.0"}`, header `X-Auth-PSK: <key>`. Timeouts 3 s. Never log or publish the PSK.
- TV Settings → "TV power control": Bravia IP (default `192.168.2.32`), Pre-Shared Key (plain text field so the phone keyboard can type into it), "Test connection" (shows "Connected — TV is on/standby", "Wrong key" on 401/403/error 40005, "Can't reach the TV" on timeout). Setup steps shown on screen: Bravia Settings → Network → Home network → IP control → Authentication "Normal and Pre-Shared Key", set a Pre-Shared Key, Remote start On. For Power On to work, the Google TV box must stay awake while the Bravia is off (box: turn off "HDMI-CEC → turn off with TV"/never sleep).
- Cleartext HTTP needed for the LAN IP: `network_security_config.xml` allows cleartext (all other endpoints are hard-coded https).

### Phone app v5
- Bottom navigation bar with two destinations: **Apps** (the existing paired view) and **Remote** (new page). Remembered per phone (`lounge_remote_tab`).
- Remote page mimics a physical remote (see brief); Power button also in the Apps page top bar.
- `?demo=1` fake TV implements power/input/key, `tv/power`, `status.keys` and `status.power`.

### Ambient screen (TV)
- Pure black (#000) screen with only a dim clock and current weather; no animation, redraws once a minute; the block shifts position every minute (burn-in). Shown after N idle minutes on Lounge Home (Settings: Off / 5 / 10 / 30, default 10) and as a system screensaver (DreamService "Lounge Ambient"). Any key dismisses it (the key is consumed).

## v6 — Minimal dock Home, HDMI tiles, Webb backgrounds (TV APP_VERSION 6)

### Home ("Minimal dock")
- Background: a James Webb photo (Cosmic Cliffs / Pillars of Creation / Deep Field, bundled in `drawable-nodpi`, decoded once at inSampleSize 2), Crop, under a black scrim (Settings → Background → Dim: Light 30 % / Medium 50 % / Strong 70 %) plus a bottom gradient. "Black" = plain Background colour. Prefs (lounge_prefs): `lounge_bgImage` (cliffs|pillars|deep|black), `lounge_bgDim` (30|50|70), `lounge_ambientStyle` (webb|black). Changes apply to Home immediately.
- Level 1 (one screen): top-left big clock (~1.3× displayLarge), date, weather line; top-right Now Playing pill + settings button (Up from the dock reaches settings). Lower part: optional update banner / tip, then ONE dock row: [Continue card if any history] + favourites + an "All apps" tile (dots glyph) last; footer hint. Initial focus: first favourite, else the All apps tile.
- Level 2 (Down, a full page below): "Inputs" row and "Recently watched" row; each row restores its last focused item.
- All apps drawer: full-screen overlay (Webb background, darker scrim), title "All apps", 6-column grid of every visible app (favourites + others, A→Z case-insensitive; hidden apps stay hidden). OK launches, hold-OK opens the tile action sheet, Back closes and returns focus to the All apps tile. The Home button also closes it.
- Tint: the focused app's dominant icon/banner colour (hue-bucket average, ignoring grey/very dark/very light pixels; fallback Primary) fades in over ~600 ms as a radial glow (~30 % alpha) at the bottom of the background and tints the focus ring.

### HDMI tiles (Bravia)
- `avContent.getCurrentExternalInputsStatus` (v1.1, falls back to 1.0) → HDMI inputs (uri `extInput:hdmi?port=N`, label/title, `connection`), active input from `getPlayingContentInfo`. Select → `setPlayContent {uri}`; failure → snack "Couldn't switch to …".
- Tile: name, "HDMI N · Connected/Not connected", green dot (#4CD97B) when connected, "Now showing" chip on the active input, dimmer when disconnected.
- States: not configured → "Set up TV control" (opens Settings); wrong key → "TV key rejected – check Settings"; unreachable/error → "TV not reachable" (select retries); loading → placeholder tiles.
- Refresh on Home resume, when level 2 first gets focus, and every 30 s while level 2 is focused (and Lounge is resumed).

### Continue / Recently watched
- MainActivity's MediaWatcher reports (with Notification access) record each PLAYING session with a non-blank title: package, app label, title, subtitle, art (≤400 px PNG in filesDir/media_art). Stored in lounge_prefs `lounge_mediaHistory` (JSON, newest first, max 12, same package+title deduped, writes throttled to 30 s for the same item). Orphan art is cleaned at start.
- Continue card (level 1, ~1.6× favourite width): art, else the app banner/icon; title + "App · 2 h ago". OK opens the app; hold-OK → "Remove from history".
- Recently watched (level 2): smaller cards. With no history and no Notification access: a hint card "Turn on Notification access to see what you've watched" that opens the access settings. Nothing in level 1 when history is empty.

### Ambient
- Settings → Ambient screen → Style: Webb photo (default; the selected Home photo at ~30 % brightness, scaled 1.18× and slowly panning, ~90 s per leg) or Black (v5 pure black). Black background choice also means a black ambient screen. Applies to the in-app ambient screen and the "Lounge Ambient" screensaver.

### Credits
- Settings → About: "Background photos: NASA, ESA, CSA, STScI (ESA/Webb, CC BY 4.0)".
