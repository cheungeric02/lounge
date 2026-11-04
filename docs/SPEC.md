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
- Phone app: `remote/` in this repo (single self-contained `index.html` + `manifest.webmanifest` + `sw.js` + icons). CI copies it to `site/remote/` → `https://cheungeric02.github.io/lounge/remote/`. Installable PWA, scope `/lounge/remote/`.
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
