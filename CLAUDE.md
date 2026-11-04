# Lounge

Android TV home-screen app. Kotlin 2.0 + Jetpack Compose + androidx.tv material, single `:app` module, package `ca.cheungeric.lounge`, minSdk 28 / targetSdk 34.

## Commands (run from repo root, PowerShell or Git Bash)
- Build: `./gradlew assembleDebug` (release: `assembleRelease`)
- Test: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Toolchain is per-user: `JAVA_HOME=%LOCALAPPDATA%\Programs\jdk-17`, SDK at `%LOCALAPPDATA%\Android\Sdk` (in `local.properties`, gitignored).

## Rules
- No admin rights on this PC — never use installers that need elevation; per-user zips only.
- Public repo: never commit keystores, passwords or `keystore.properties`. Release key lives in `%USERPROFILE%\.lounge-keys\` and CI secrets `LOUNGE_*`.
- `APP_VERSION` in `app/src/main/java/ca/cheungeric/lounge/ui/patchnotes/PatchNotes.kt` is the only version source (Gradle reads it). Bump it and add a `PATCH_NOTES` entry every release.
- No new dependencies beyond SPEC "Stack" without a reason in the task report. No Hilt, no Retrofit.
- Never add kid/parental features, and never add stream scraping/debrid/torrent features.
- Don't touch the manifest unless your task says so; T1 declares everything up front.
- Workers don't commit or push; update `filemap.md` when adding/moving files.

## Docs
- `docs/SPEC.md` — what to build (design tokens, flows, versioning rules)
- `docs/PLAN.md` — task list and status
- `filemap.md` — where things live
