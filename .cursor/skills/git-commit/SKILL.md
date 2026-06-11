---
name: git-commit
description: Creates git commits following TPlayer Conventional Commits and safety rules. Use when the user asks to commit, create a commit, save changes to git, or says "закоммить"/"сделай коммит".
---

# Git Commit (TPlayer)

Create commits only when the user explicitly asks. Never commit unless requested.

## Workflow

### 1. Inspect (run in parallel)

```bash
git status
git diff
git diff --staged
git log -5 --oneline
```

### 2. Draft message

- **Format:** Conventional Commits in **English**
- **Types:** `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`
- **Scope (optional):** `android`, `desktop`, `indexer`, `api`, `docker`
- **Focus on why**, 1–2 sentences in subject; body only if needed
- Match recent repo style from `git log`

Examples:
```
feat(desktop): load client config from root .env file
fix(indexer): include checksum and duration in track_files upsert
docs: remove deploy-vps script, use docker compose only
```

### 3. Safety checks (block commit if violated)

- **Never** commit `.env`, credentials, tokens, or secrets — warn if staged
- **Never** `git config` changes, `--no-verify`, `--amend` (unless all amend rules below met), force push
- **Never** empty commit when nothing changed
- Only commit files relevant to the change

**Amend only if ALL true:** user asked to amend; HEAD commit was created this session; not pushed to remote.

**If commit hook fails:** fix issues, create a **new** commit — do not amend a failed commit.

### 4. Stage and commit

Stage only intended files, then commit.

**PowerShell (Windows):**
```powershell
git add path/to/files
git commit -m @'
feat(scope): short subject line

Optional body explaining why.
'@
git status
```

**bash:**
```bash
git add path/to/files
git commit -m "$(cat <<'EOF'
feat(scope): short subject line

Optional body explaining why.
EOF
)"
git status
```

### 5. After commit

- Confirm success from `git status`
- Do **not** push unless the user explicitly asks

## Scope hints (this repo)

| Path | Scope |
|------|-------|
| `apps/android/` | `android` |
| `apps/desktop/` | `desktop` |
| `backend/indexer/` | `indexer` |
| `backend/api/` | `api` |
| `docker-compose.yml`, `docker/` | `docker` |
| `.cursor/`, `AGENTS.md`, `README.md` | `docs` or `chore` |

## Do not

- Commit the plan file or unrelated drive-by changes
- Use Russian in commit messages
- Push to remote without explicit user request
