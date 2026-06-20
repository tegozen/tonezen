---
name: bump
description: Use when bumping Tonezen client app versions, cutting release builds, creating version tags, or when the user says "/bump", "bump version", "подними версию", "бамп", "релиз с новой версией".
---

# Bump & Release (Tonezen)

End-to-end release cut: **infer SemVer bump from git history → bump versions → commit → build-release → tag**.

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

- New release tags must use the conventional `vX.Y.Z` format, e.g. `v0.1.12`.
- For backward compatibility only, previous-release detection may read existing historical tags like `0.1.11` or `Tonezen-0.1.11`.
- Infer the required bump from commits and file changes since the latest release tag.
- **Breaking change:** major (`1.2.3` → `2.0.0`)
- **Feature:** minor (`1.2.3` → `1.3.0`)
- **Fix/perf/refactor/runtime dependency update:** patch (`1.2.3` → `1.2.4`)
- **Docs/ci/tests/agent-only changes:** no client release; stop and ask before bumping.
- **User says patch/minor/major:** treat it as an override only if it is not lower than the inferred bump.
- **User gives exact version** (e.g. `0.2.0`): set that semver everywhere only if it is greater than the latest tag and not lower than the inferred bump.

After bump, `versionName` (Android) must equal `package.json` `version` (desktop).

If the SemVer decision is ambiguous, **stop and ask**. Do not silently default to patch.

## Workflow

```
Bump release:
- [ ] Pre-flight (clean tree, latest release tag found, versions aligned, .env exists)
- [ ] Changes since latest tag inspected and SemVer bump selected
- [ ] Versions bumped in all client files
- [ ] Commit created
- [ ] build-release executed (read `.cursor/skills/build-release/SKILL.md`)
- [ ] Artifacts copied to landing downloads
- [ ] Version tag created on the release commit
- [ ] Summary reported
```

### 1. Pre-flight

Run in parallel:

```powershell
git status
git diff
git log -3 --oneline
git tag --list
```

- If unrelated uncommitted changes exist: **stop** and ask user to commit/stash first, or confirm scope.
- Find the latest release tag:

```powershell
$releaseTags = git tag --list | Where-Object { $_ -match '^(v|tonezen-)?\d+\.\d+\.\d+$' } | ForEach-Object {
  $version = $_ -ireplace '^(v|tonezen-)', ''
  [pscustomobject]@{ Tag = $_; Version = [version]$version }
} | Sort-Object Version -Descending
$lastRelease = $releaseTags | Select-Object -First 1
$lastTag = $null
$lastVersion = $null
if ($lastRelease) {
  $lastTag = $lastRelease.Tag
  $lastVersion = $lastRelease.Version.ToString()
}
```

- If `$lastTag` is empty: **stop** and ask for an initial exact version/tag strategy.
- Read current `versionName` / `versionCode` / desktop `version` — they must already match `$lastVersion`.
- Confirm root `.env` has `TONEZEN_BASE_URL` and `ANON_KEY` (needed for build-release).
- If `HEAD` already points at `$lastTag` and there are no changes since it: **stop**; there is nothing to release.

### 2. Infer SemVer bump

Inspect both commit messages and touched paths:

```powershell
git log --format="%H %s" "$lastTag..HEAD"
git log --format="%B%n---END-COMMIT---" "$lastTag..HEAD"
git diff --name-status "$lastTag..HEAD"
```

Choose the highest required bump:

| Signal | Bump |
|--------|------|
| `BREAKING CHANGE:` / `BREAKING-CHANGE:` in commit body, or `!` in Conventional Commit type/scope (`feat!:` / `feat(api)!:`) | major |
| Backward-compatible user feature (`feat:` / `feat(scope):`) | minor |
| Runtime bug fix, performance fix, refactor that changes shipped client behavior, revert of shipped behavior, or runtime dependency update | patch |
| Only docs, tests, CI, build metadata, agent skills, comments, or non-client files | none; stop and ask |

Rules:

- Use `major > minor > patch > none`.
- If commit messages are non-conventional, inspect the diff and classify by behavior.
- If a requested override is lower than the inferred bump, stop and ask for confirmation.
- If an exact requested version is `<= $lastVersion`, stop; never reuse or move a release tag.
- For stable `X.Y.Z` tags, compare versions as `[version]` values in PowerShell, not as strings.

### 3. Bump versions

**Desktop** (from repo root):

```powershell
cd apps/desktop
npm version patch --no-git-tag-version
```

Replace `patch` with the inferred `minor` or `major` when required. For an explicit version:

```powershell
npm version 0.2.0 --no-git-tag-version
```

Note the new version from `package.json`.

**Android** — edit `apps/android/app/build.gradle.kts`:

- `versionCode = <previous + 1>`
- `versionName = "<new semver from desktop>"`

Before committing, confirm no tag already exists for the new version:

```powershell
$newTag = "vX.Y.Z"
git rev-parse -q --verify "refs/tags/$newTag"
```

If it exists, **stop**. Do not delete, move, or overwrite release tags.

### 4. Commit

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

### 5. Build release

Read and execute **[build-release](../build-release/SKILL.md)** in full:

1. `apps/android` → `.\gradlew.bat assembleRelease`
2. `apps/desktop` → `npm install` (if needed) → `npm run dist:win`

Run Android and Windows builds in parallel when possible.

### 6. Copy to landing (default for bump)

Unlike standalone build-release, **always copy** after successful builds:

```powershell
Copy-Item -Force apps/android/app/build/outputs/apk/release/app-release.apk docker/landing/public/downloads/tonezen-android.apk
Copy-Item -Force apps/desktop/release/tonezen-windows.exe docker/landing/public/downloads/tonezen-windows.exe
```

### 7. Tag release

Create the tag only after successful builds and landing copies:

```powershell
$newTag = "vX.Y.Z"
git tag -a $newTag -m $newTag
git describe --tags --exact-match HEAD
```

The tag must point at the release bump commit. Do **not** push the commit or tag unless the user explicitly asks.

### 8. Report

Tell the user:

- Old → new version
- Inferred SemVer bump and the evidence used
- Commit hash
- Tag name
- Artifact paths and file sizes
- Landing copy paths
- Reminder: push commit + tag and upload binaries to prod server if needed

## Do not

- Commit `.env`, `client.env`, APK/EXE, or `docker/landing/public/downloads/*`
- Bump without committing then building — all three steps are one workflow
- Push commit or tags to remote unless user explicitly asks
- Move, delete, or overwrite an existing release tag
- Default to patch when the SemVer classification is ambiguous
- Run full `make test` unless build failed for code reasons

## Failure handling

| Failure | Action |
|---------|--------|
| No release tag exists | Stop and ask for initial version/tag strategy |
| No releasable client changes since latest tag | Stop and report that no bump is needed |
| SemVer classification is ambiguous | Stop and ask user to choose patch/minor/major/exact |
| Requested bump is lower than inferred bump | Stop and ask for explicit confirmation |
| New version tag already exists | Stop; do not move or overwrite the tag |
| Commit hook fails | Fix, new commit — do not amend failed commit |
| Build fails after commit | Report commit hash; do not create tag; user fixes code and re-runs build-release only |
| Tag creation fails after successful build | Report commit hash and artifacts; do not push anything |
| Version mismatch after bump | Fix files before committing |
