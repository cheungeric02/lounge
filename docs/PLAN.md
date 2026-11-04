# Lounge — plan

Status legend: [ ] todo · [~] in progress · [x] accepted. Workers never commit; the orchestrator commits after review.

## T1 — Scaffold (Medium) · deps: none
Install JDK 17 (Temurin zip) to `%LOCALAPPDATA%\Programs\jdk-17` and Android cmdline-tools to `%LOCALAPPDATA%\Android\Sdk` per-user (no admin; set User env `JAVA_HOME`, `ANDROID_HOME`, add to User PATH), then `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"` and accept licenses. Create the Gradle project per SPEC "Stack": wrapper 8.11.1, version catalog `gradle/libs.versions.toml`, `:app` module, package `ca.cheungeric.lounge`, `MainActivity` showing a placeholder Compose TV "Lounge" screen, full manifest per SPEC (HOME + LEANBACK_LAUNCHER filters, singleTask, `<queries>` for LAUNCHER/LEANBACK_LAUNCHER intents, INTERNET + REQUEST_INSTALL_PACKAGES permissions, a declared-but-stub `HomeButtonService` with its accessibility XML, a FileProvider if needed) so later tasks don't edit the manifest concurrently. `ui/patchnotes/PatchNotes.kt` with `APP_VERSION = 1` and Gradle reading it for versionCode/versionName. Signing config reading `%USERPROFILE%\.lounge-keys\keystore.properties`. Generate the release keystore with keytool there (never in the repo). Stub empty packages/files listed in SPEC architecture as minimal compiling stubs (interfaces + data classes, TODO bodies). Add `.github/workflows/release.yml` per SPEC. Create public repo `cheungeric02/lounge` with `gh repo create --public` (no push), set the four secrets with `gh secret set`, enable Pages with source "GitHub Actions". Update `filemap.md`.
Verify: `gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug` passes; release APK is signed (`apksigner verify --print-certs`); `gh secret list` shows the four names.

## T2 — Data layer (Medium) · deps: T1
Implement `AppsRepository` (PackageManager query of LEANBACK_LAUNCHER ∪ LAUNCHER activities, dedupe by package, exclude self, banner/icon loading, refresh on PACKAGE_ADDED/REMOVED/CHANGED via a receiver registered in code), `LayoutPrefs` (DataStore), pure `HomeLayout` (default favorites per SPEC on first run, favorites order, hide, move left/right, all-apps alphabetical with stable custom order), `WeatherRepository` (Open-Meteo URL from SPEC, WMO-code mapping, 30-min refresh, cache in DataStore, fail-safe). Unit tests for HomeLayout (first-run defaults, uninstalled favorite dropped, move at edges, hide/unhide, ordering stability) and weather JSON parsing + WMO mapping. Touch only `data/` and its tests (+ AppContainer wiring in `LoungeApp`).
Verify: unit tests pass; `assembleDebug` passes.

## T3 — Home UI (Careful) · deps: T1 · parallel with T2, T5, T6
Build `ui/theme` and `ui/home` per SPEC "Home screen" + theme tokens, against the T1 model stubs using fake data in `@Preview`s: ClockHeader (ticking each minute, 12-hour), WeatherCard (state: loading/hidden, value), AppRow + AppTile (banner or icon fallback, spring focus scale + outline, click + long-press callbacks), ContinueWatchingPlaceholder (behind a flag, off), footer hint, overscan padding, D-pad focus order (initial focus on first favorite; Up from favorites reaches settings button). Touch only `ui/theme`, `ui/home`.
Verify: `assembleDebug` + `lintDebug` pass; previews compile; screenshot of a preview render if a Compose preview screenshot is feasible, otherwise describe.

## T4 — Wiring + edit sheet + settings (Medium) · deps: T2, T3, T5, T6
Connect HomeScreen to repositories via a `HomeViewModel` (StateFlow). Tile action sheet (`ui/edit`) per SPEC. Launch apps on click (handle missing launch intent gracefully). `SettingsScreen`: Hidden apps (unhide), Home button status + open Accessibility settings, Check for updates, Patch Notes (from T6 renderer), About (version, repo URL). Home ↔ Settings navigation with Back. Onboarding tip when home service disabled. Update banner on Home when available.
Verify: `assembleDebug testDebugUnitTest lintDebug` pass; ViewModel unit test for favorite/hide/move flows with a fake prefs store.

## T5 — Home-button override (Careful) · deps: T1 · parallel
Implement `HomeButtonService` per SPEC "Home button" (key filter + window-change fallback, debounce, never loop when Lounge is already foreground, safe on API 28–34). Expose `isHomeServiceEnabled(context)` helper. Touch only `home/` and `res/xml/` accessibility config.
Verify: builds; unit-test the pure decision logic (`shouldLaunchLounge(event, lastHomePressAt, now, foregroundPkg)`).

## T6 — Self-update + Pages + Patch Notes (Careful) · deps: T1 · parallel
`update/UpdateChecker` + `UpdateInstaller` per SPEC (version.json, sha256 verify, PackageInstaller session with a status receiver, "Install unknown apps" permission flow on API 26+). Finish the CI workflow: generate `version.json` (versionCode from APP_VERSION, sha256 of the APK), `index.html` download page, deploy-pages. `ui/patchnotes`: PATCH_NOTES v1 entry, shared `PatchNoteEntry`, `WhatsNewDialog`, `checkWhatsNew` gate per SPEC with unit tests for all four branches. Touch only `update/`, `ui/patchnotes/`, `.github/`.
Verify: unit tests (version compare, gate branches, version.json parse); `assembleRelease` passes; workflow YAML valid (`actionlint` if available, else careful read).

## Close (orchestrator)
Full build + tests, push `main`, watch CI deploy, confirm Pages URLs serve, hand Eric the first-install steps.
