---
name: bump
description: Bumps Tonezen client app versions, commits the change, then runs the build-release skill for Windows and Android. Use when the user says "/bump", "bump version", "подними версию", "бамп", "релиз с новой версией", or wants version bump + commit + prod builds in one flow.
---

# Bump & Release (Tonezen)

End-to-end release cut: **bump versions → commit → build-release**.

Explicit `/bump` invocation **includes the commit** (overrides git-commit “only when asked” for this workflow only).

## Version files

Keep these in sync (same semver):

| File | Fields |
|------|--------|
| `apps/android/app/build.gradle.kts` | `versionName`, `versionCode` (+1 each bump) |
| `apps/desktop/package.json` | `version` |
| `apps/desktop/package-lock.json` | top-level `version` and `packages[""].version` |

Do **not** bump backend `package.json` versions — clients only.

## Bump rules

- **Default:** patch (`0.1.4` → `0.1.5`)
- **User says minor/major:** use `npm version minor|major --no-git-tag-version` in `apps/desktop`
- **User gives exact version** (e.g. `0.2.0`): set that semver everywhere; still increment `versionCode` by 1

After bump, `versionName` (Android) must equal `package.json` `version` (desktop).

## Workflow

```
Bump release:
- [ ] Pre-flight (clean tree, versions aligned, .env exists)
- [ ] Versions bumped in all client files
- [ ] Commit created
- [ ] build-release executed (read `.cursor/skills/build-release/SKILL.md`)
- [ ] Artifacts copied to landing downloads
- [ ] Summary reported
```

### 1. Pre-flight

Run in parallel:

```powershell
git status
git diff
git log -3 --oneline
```

- If unrelated uncommitted changes exist: **stop** and ask user to commit/stash first, or confirm scope.
- Read current `versionName` / `versionCode` / desktop `version` — they must already match.
- Confirm root `.env` has `TONEZEN_BASE_URL` and `ANON_KEY` (needed for build-release).

### 2. Bump versions

**Desktop** (from repo root):

```powershell
cd apps/desktop
npm version patch --no-git-tag-version
```

Replace `patch` with `minor` or `major` when requested. For an explicit version:

```powershell
npm version 0.2.0 --no-git-tag-version --allow-same-version
```

Note the new version from `package.json`.

**Android** — edit `apps/android/app/build.gradle.kts`:

- `versionCode = <previous + 1>`
- `versionName = "<new semver from desktop>"`

### 3. Commit

Follow [git-commit](../git-commit/SKILL.md) safety rules (no secrets, no `--no-verify`).

Stage only version files:

```powershell
git add apps/android/app/build.gradle.kts apps/desktop/package.json apps/desktop/package-lock.json
git commit -m @'
chore(release): bump app versions to X.Y.Z
'@
git status
```

Replace `X.Y.Z` with the actual new version. Do **not** push unless user asks.

### 4. Build release

Read and execute **[build-release](../build-release/SKILL.md)** in full:

1. `apps/android` → `.\gradlew.bat assembleRelease`
2. `apps/desktop` → `npm install` (if needed) → `npm run dist:win`

Run Android and Windows builds in parallel when possible.

### 5. Copy to landing (default for bump)

Unlike standalone build-release, **always copy** after successful builds:

```powershell
Copy-Item -Force apps/android/app/build/outputs/apk/release/app-release.apk docker/landing/public/downloads/tonezen-android.apk
Copy-Item -Force apps/desktop/release/tonezen-windows.exe docker/landing/public/downloads/tonezen-windows.exe
```

### 6. Report

Tell the user:

- Old → new version
- Commit hash
- Artifact paths and file sizes
- Landing copy paths
- Reminder: push commit and upload binaries to prod server if needed

## Do not

- Commit `.env`, `client.env`, APK/EXE, or `docker/landing/public/downloads/*`
- Bump without committing then building — all three steps are one workflow
- Push to remote unless user explicitly asks
- Run full `make test` unless build failed for code reasons

## Failure handling

| Failure | Action |
|---------|--------|
| Commit hook fails | Fix, new commit — do not amend failed commit |
| Build fails after commit | Report commit hash; user fixes code and re-runs build-release only |
| Version mismatch after bump | Fix files before committing |
