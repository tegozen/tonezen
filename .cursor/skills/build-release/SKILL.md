---
name: build-release
description: Builds production Windows and Android client releases for Tonezen. Use when the user asks to build release/prod binaries, create distributables, says "собери релиз", "prod сборка", "/build-release", or wants tonezen-windows.exe and tonezen-android.apk.
---

# Build Release (Tonezen)

Build production **Windows desktop** and **Android** clients. Run commands yourself — do not only print instructions.

## Outputs

| Platform | Command            | Artifact                                                     |
| -------- | ------------------ | ------------------------------------------------------------ |
| Android  | `assembleRelease`  | `apps/android/app/build/outputs/apk/release/app-release.apk` |
| Windows  | `npm run dist:win` | `apps/desktop/release/tonezen-windows.exe`                   |

Landing page expects these names under `docker/landing/public/downloads/` (git-ignored):

- `tonezen-android.apk`
- `tonezen-windows.exe`

## Pre-flight

1. **Root `.env`** must exist with `TONEZEN_BASE_URL` and `ANON_KEY` (desktop `dist:win` reads them via `prepare-client-env.mjs`).
2. **Android config** — verify `BASE_URL` and `SUPABASE_ANON_KEY` in `apps/android/app/build.gradle.kts` match production (not localhost).
3. **Versions aligned** — `versionName` / `versionCode` in `build.gradle.kts` and `version` in `apps/desktop/package.json` should match before release.
4. **Never commit** `.env`, `client.env`, APK/EXE, or `docker/landing/public/downloads/*` binaries.

If `.env` is missing: tell the user to run `make gen-env` and fill `TONEZEN_BASE_URL` + `S3_*`; do not invent secrets.

## Workflow

Copy checklist and track progress:

```
Release build:
- [ ] Pre-flight checks passed
- [ ] Android release APK built
- [ ] Windows portable EXE built
- [ ] Artifacts copied to landing downloads
- [ ] Paths and sizes reported to user
```

### 1. Android

From repo root (PowerShell):

```powershell
cd apps/android
.\gradlew.bat assembleRelease
```

On failure: check JDK 17+, Android SDK, `ANDROID_HOME`. Run unit tests only if user asked or build failed for code reasons:

```powershell
.\gradlew.bat testDebugUnitTest
```

### 2. Windows desktop

Ensure dependencies once per machine / after `package.json` changes:

```powershell
cd apps/desktop
npm install
npm run dist:win
```

`dist:win` runs: `prepare-client-env.mjs` → `electron-vite build` → `electron-builder --win portable`.

### 3. Copy to landing (optional)

Only when user wants local landing downloads updated:

```powershell
Copy-Item -Force apps/android/app/build/outputs/apk/release/app-release.apk docker/landing/public/downloads/tonezen-android.apk
Copy-Item -Force apps/desktop/release/tonezen-windows.exe docker/landing/public/downloads/tonezen-windows.exe
```

Restart or refresh landing container if already running (`docker compose up -d` serves static files from that folder).

## After build

Report to the user:

- Full paths to both artifacts
- File sizes
- App version from `build.gradle.kts` / `package.json`
- Reminder: binaries are not in git; upload or copy to server manually for production landing

## Do not

- Run `make test` / full CI unless user asked — release builds are slow enough
- Commit release artifacts or `.env`
- Change signing keys or production URLs without user confirmation
- Build macOS (`dist:mac` does not exist yet — only document if user asks)

## Parallel builds

Android Gradle and desktop npm can run in parallel on separate terminals when CPU/disk allow. Wait for both before copying to landing.
