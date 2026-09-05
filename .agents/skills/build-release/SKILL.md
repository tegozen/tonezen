---
name: build-release
description: Builds production Windows, Android, and macOS client releases for Tonezen. Use when the user asks to build release/prod binaries, create distributables, says "собери релиз", "prod сборка", "/build-release", or wants Tonezen client artifacts.
---

# Build Release (Tonezen)

Build production **Windows desktop** and **Android** clients. When the workflow runs on macOS, also build the **macOS desktop** DMG. Run commands yourself — do not only print instructions.

## Outputs

| Platform | Command            | Artifact                                                     |
| -------- | ------------------ | ------------------------------------------------------------ |
| Android  | `assembleRelease`  | `apps/android/app/build/outputs/apk/release/app-release.apk` |
| Windows  | `npm run dist:win` | `apps/desktop/release/tonezen-windows.exe`                   |
| macOS    | `npm run dist:mac` | `apps/desktop/release/tonezen-macos.dmg`                     |

Landing page expects these names under `docker/landing/public/downloads/` (git-ignored):

- `tonezen-android.apk`
- `tonezen-windows.exe`
- `tonezen-macos.dmg`
- `tonezen-android-mapping.txt.gz` (+ `tonezen-android-proguard-uuid.txt`)
- `tonezen-desktop-sourcemaps.tar.gz`

## Pre-flight

1. **Root `.env`** must exist with `TONEZEN_BASE_URL`, `ANON_KEY`, and GlitchTip keys (`GLITCHTIP_DESKTOP_PUBLIC_KEY`, `GLITCHTIP_ANDROID_PUBLIC_KEY`, … — from `node scripts/gen-env.mjs`). Desktop `dist:win` / `dist:mac` bake the desktop DSN via `prepare-client-env.mjs`. Android `assembleRelease` reads Android DSN into `BuildConfig`. Crash symbols are collected **locally** next to the binaries (`node scripts/collect-release-symbols.mjs`) — no network upload during build.
2. **Android config** — verify `BASE_URL` and `SUPABASE_ANON_KEY` in `apps/android/app/build.gradle.kts` match production (not localhost). `GLITCHTIP_DSN` is filled from root `.env` at build time.
3. **Versions aligned** — `versionName` / `versionCode` in `build.gradle.kts` and `version` in `apps/desktop/package.json` should match before release.
4. **Desktop package icons** — verify `apps/desktop/package.json` sets `build.win.icon` to `resources/app-icon.ico`, `build.mac.icon` to `resources/app-icon.icns`, and NSIS installer/uninstaller icons to `resources/app-icon.ico`; both icon files must exist.
5. **Android signing** — use the existing app key via git-ignored `apps/android/signing.properties`, Gradle properties, or environment variables; see `README.md` → Client configuration. Never generate a replacement key for an update. Missing signing configuration must fail the release build. Verify the finished APK using `apksigner verify --print-certs` and compare its certificate with the previous distributed APK before copying it.
6. **Never commit** `.env`, `client.env`, APK/EXE, or `docker/landing/public/downloads/*` binaries.

If `.env` is missing: tell the user to run `make gen-env` and fill `TONEZEN_BASE_URL` + `S3_*`; do not invent secrets. If GlitchTip keys are missing in an old `.env`, re-run `node scripts/gen-env.mjs --force` or copy the `GLITCHTIP_*` block from `.env.example` and generate values.

## Workflow

Copy checklist and track progress:

```
Release build:
- [ ] Pre-flight checks passed
- [ ] Desktop package icons verified
- [ ] Android release APK built
- [ ] Windows installer EXE built
- [ ] macOS DMG built when running on macOS
- [ ] Artifacts copied to landing downloads
- [ ] Crash symbols collected next to apps
- [ ] Paths and sizes reported to user
```

## Choose the host path

Detect the host before starting builds:

- **Native Windows:** use `gradlew.bat` and Windows `npm` directly.
- **macOS:** use `./gradlew`, `npm run dist:win`, and `npm run dist:mac` as described below.
- **WSL:** use the dedicated WSL path below. Do **not** install or use Wine.
- **Other Linux:** Android may use the WSL Docker command; stop and report that a native Windows host/runner is required for NSIS unless Windows interop is available.

Detect WSL with `uname -r` containing `microsoft`/`WSL`. Confirm Windows interop before relying on it:

```bash
test -x /mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe
test -f '/mnt/c/Program Files/nodejs/npm.cmd'
```

### WSL build path

**Android:** build in Docker so the host does not need JDK or Android SDK. Run the
container as its default user so the image can write SDK metadata. Keep the existing
release keystore under the mounted checkout (git-ignored), referenced by
`apps/android/signing.properties`. Persisting the Gradle cache does not preserve a
debug key under `/opt/android-sdk/.android`; release builds must use the explicit key.

```bash
docker pull mingc/android-build-box:latest
docker run --rm \
  -v tonezen-gradle-cache:/root/.gradle \
  -v "$(pwd):/project" \
  -w /project/apps/android \
  mingc/android-build-box:latest \
  ./gradlew assembleRelease
```

The first pull is several gigabytes. Keep the image cached for later releases.

**Windows:** build with native Windows Node through `powershell.exe`, never Wine.
Do not run Windows `npm install` in the WSL checkout: Linux and Windows create
incompatible `node_modules/.bin` entries, and NSIS can hang on the UNC filesystem.

1. Resolve the checkout with `wslpath -w`.
2. Create a version-specific directory under `%LOCALAPPDATA%\Temp`.
3. Copy `apps/desktop` there with `robocopy`, excluding `node_modules`, `out`, `release`, and `client.env`; treat exit codes `0..7` as success.
4. Copy the root `.env` to the temporary monorepo root for `prepare-client-env.mjs`.
5. From the local `C:` copy, run `npm.cmd install` and `npm.cmd run dist:win`.
6. Copy `release/tonezen-windows.exe` and `out/**/*.map` back to the WSL checkout. The maps are required by `collect-release-symbols.mjs`.
7. Delete the temporary directory in a `finally` block because it contains `.env`.

If an earlier Linux/container attempt populated `apps/desktop/node_modules` or
`out`, move those generated directories to unique `/tmp` paths before a Windows
retry. Never delete or overwrite tracked source files.

### 1. Android

From repo root (PowerShell):

```powershell
cd apps/android
.\gradlew.bat assembleRelease
```

On failure: check JDK 17+, Android SDK, `ANDROID_HOME`. Android has no unit-test suite; do not run `testDebugUnitTest`.
### 2. Windows desktop

Ensure dependencies once per machine / after `package.json` changes:

```powershell
cd apps/desktop
npm install
npm run dist:win
```

`dist:win` runs: `prepare-client-env.mjs` -> `electron-vite build` -> `electron-builder --win nsis`.

The electron-builder log must not contain `default Electron icon is used`; that warning means the Windows executable will show the stock Electron process icon.

### 3. macOS desktop (macOS hosts only)

Skip this step unless the current host OS is macOS (`process.platform === "darwin"` / `uname` returns `Darwin`). Electron-builder can only produce the DMG on macOS.

```bash
cd apps/desktop
npm install
npm run dist:mac
```

`dist:mac` runs: `prepare-client-env.mjs` -> `electron-vite build` -> `electron-builder --mac dmg`. The package config sets `mac.identity: null`, `mac.notarize: false`, and `dmg.sign: false`; do not add signing or notarization unless the user explicitly asks.

### 4. Copy to landing (always)

**Always run this step** after all requested builds succeed — do not ask the user and do not leave copy instructions for them to run manually.

From **repo root**. Ensure the folder exists, then copy every artifact that was built in this run:

```powershell
$dest = "docker/landing/public/downloads"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

Copy-Item -Force apps/android/app/build/outputs/apk/release/app-release.apk "$dest/tonezen-android.apk"
Copy-Item -Force apps/desktop/release/tonezen-windows.exe "$dest/tonezen-windows.exe"
```

If the macOS step ran and produced a DMG, also copy:

```powershell
Copy-Item -Force apps/desktop/release/tonezen-macos.dmg "$dest/tonezen-macos.dmg"
```

Then collect crash symbols into the same folder (no network):

```powershell
node scripts/collect-release-symbols.mjs
```

Produces (when sources exist): `tonezen-android-mapping.txt.gz`, `tonezen-android-proguard-uuid.txt`, `tonezen-desktop-sourcemaps.tar.gz`. FTP them with the apps; landing does not link them.

Copy only files that exist — skip missing platform outputs (e.g. no DMG on Windows hosts).

If landing is already running locally, refreshed static files are served from that folder (`docker compose up -d`); no container restart required for file swaps.

## After build

Report to the user:

- Full paths to all built artifacts (source + landing copy)
- File sizes
- App version from `build.gradle.kts` / `package.json`
- Confirm landing copies under `docker/landing/public/downloads/` (apps + symbol archives)
- Reminder: binaries/symbols are not in git; for production VPS, upload the whole `downloads/` folder (apps + mapping/sourcemaps) together

## Do not

- Run `make test` / full CI unless user asked — release builds are slow enough
- Commit release artifacts or `.env`
- Change signing keys or production URLs without user confirmation
- Sign or notarize macOS builds unless user explicitly asks

- Use Wine for Windows packaging from WSL
- Run Windows `npm install` against a WSL checkout containing Linux dependencies
- Leave a temporary Windows build directory containing the copied root `.env`

## Parallel builds

Android Gradle and desktop npm can run in parallel on separate terminals when CPU/disk allow. On macOS, the Windows and macOS desktop builds can also run sequentially from `apps/desktop`. **Wait for all builds to finish, then run step 4 once** to copy every produced artifact to landing downloads.
