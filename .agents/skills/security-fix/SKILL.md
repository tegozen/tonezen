---
name: security-fix
description: Analyzes code for security vulnerabilities and applies fixes with minimal safe diffs. Use when the user asks to find or fix security holes, audit and remediate code, says "/security-fix", "security fix", "уязвимости", "дыры в безопасности", "безопасность", or wants remediation beyond a read-only review.
---

# Security Fix

Find security issues in scope, fix them, verify, and report. Unlike read-only review skills, this skill **changes code** to close vulnerabilities.

## 1. Resolve scope

| Situation | Action |
|-----------|--------|
| User named file(s) or directory | Use those paths |
| User asked to fix findings from a prior review | Scope = reported locations + related call sites |
| Open files / recent edits imply scope | Confirm in one sentence, then proceed |
| No scope | **Ask** what to audit — default to changed files on current branch |

Do not scan the entire monorepo unless the user asked for a full audit.

## 2. Read before changing

1. [`AGENTS.md`](../../../AGENTS.md) — **Forbidden** section and auth/sync rules
2. [`.cursor/rules/architecture.mdc`](../../rules/architecture.mdc) — layer boundaries, offline-first auth
3. Stack checklist in [reference.md](reference.md) for paths in scope
4. Surrounding code and existing tests

## 3. Discovery

Copy and track:

```
Security fix progress:
- [ ] Scope confirmed
- [ ] Context read (AGENTS.md, reference checklist)
- [ ] Issues triaged (severity, exploitability)
- [ ] Fixes applied (minimal diff)
- [ ] Tests added/updated where fixes need proof
- [ ] Lint/tests run on touched areas
```

### Option A — Subagent triage (optional)

When scope is branch/working-tree changes and a broad pass helps, launch one `security-review` subagent (`readonly: true`) using the same prompt shape as the `review-security` skill (`Full Repository Path`, `Diff: branch changes` or `uncommitted changes`).

Use findings as the fix backlog. Do not stop at the report — proceed to fixes unless the user said review-only.

### Option B — Direct analysis

Search scope for:

- Secrets, tokens, credentials in source or committed config
- Missing or weak auth on mutating/sensitive endpoints
- SQL injection (dynamic SQL without parameterization)
- IDOR / missing ownership checks (user can access another user's data)
- RLS bypass (`service_role` misuse, policies missing or too permissive)
- Path traversal in file/storage paths
- Unsigned or overly broad storage/download URLs
- XSS (unsanitized HTML, `dangerouslySetInnerHTML`, injection in Electron renderer)
- Insecure IPC (Electron preload exposing raw Node APIs)
- JWT mishandling (offline logout, sync exp on main thread, weak validation)
- Logging of tokens, passwords, or PII

## 4. Triage

Sort findings by severity before editing:

| Severity | Examples | Fix priority |
|----------|----------|--------------|
| **Critical** | Secrets in repo, auth bypass, RLS bypass, arbitrary file read | Fix immediately |
| **High** | IDOR, SQLi, XSS with user-controlled input, service role in client | Fix in this pass |
| **Medium** | Missing rate limits, verbose errors leaking internals, weak defaults | Fix if in scope |
| **Low** | Defense-in-depth, logging hygiene | Fix if trivial; otherwise note |

If a fix changes product behavior (e.g. stricter auth), state the behavior change briefly in the report.

## 5. Fix principles

| Principle | In practice |
|-----------|-------------|
| **Minimal diff** | Close the hole; no unrelated refactors |
| **Prefer existing patterns** | Match auth middleware, RLS, signing helpers already in repo |
| **No new forbidden patterns** | See AGENTS.md Forbidden — never bypass RLS, commit secrets, or sync music progress |
| **Auth offline-safe** | Expired JWT offline ≠ logout; no blocking cold start on sync JWT check |
| **API-first** | If fix needs a contract change → `docs/openapi.yaml` → tests → code |
| **Tests when valuable** | Add regression tests for auth, signing, merge rules, path validation — not trivial asserts |

### Tonezen-specific guardrails

- Audiobook progress: authenticated + ownership checks; music progress stays local only
- Downloads: signed URLs via existing `storageSign` helpers; no direct volume access without signing
- Desktop: no `app.quit()` on window close; tray lifecycle unchanged unless fixing a security bug there
- Migrations: explicit RLS; snake_case; no policy that grants `anon` write to user data
- Clients: secrets only from env / secure storage — never hardcoded in renderer or APK strings

## 6. Verify

Run targeted checks for touched stacks:

```bash
make lint    # or scoped: cd backend/api && npm run lint
make test    # or package-specific test command
```

For Android: `./gradlew testDebugUnitTest` when Android security paths changed.

Re-check fixed locations mentally: exploit path closed, no regression in legitimate flows.

## 7. Report

Summarize for the user:

**Scope** — paths audited

**Fixed** — table sorted by severity:

| Severity | Location | Issue | Fix |
|----------|----------|-------|-----|
| High | `path:line` | Short issue | Short remediation |

**Verified** — lint/tests run (or what was skipped and why)

**Deferred** — issues not fixed (out of scope, needs product decision) with one-line reason

**Follow-ups** — only if clearly useful (e.g. migration for RLS gap)

Do not commit unless the user explicitly asks (see [git-commit](../git-commit/SKILL.md)).

## Examples

**User:** `/security-fix backend/api`

→ Read AGENTS.md + reference backend section → audit routes/middleware/lib → fix auth/validation gaps → run `cd backend/api && npm test` → report table.

**User:** «исправь уязвимости в apps/desktop»

→ Scope `apps/desktop/` → check IPC, preload surface, XSS, env handling → fix → desktop lint/tests → report.

**User:** «security fix» after a review listed 3 findings

→ Scope = finding locations → fix those three → add tests where needed → report only fixed items.

## Additional resources

- Stack checklists and common smells → [reference.md](reference.md)
- Read-only review without fixes → use `review-security` skill instead
