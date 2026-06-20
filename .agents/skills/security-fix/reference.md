# Security Fix Reference (Tonezen)

Stack-specific checks for [SKILL.md](SKILL.md). Read sections matching audit scope.

## Repo layout → security surface

| Path | Primary risks |
|------|----------------|
| `backend/api/src/routes/` | Missing auth, input validation, IDOR |
| `backend/api/src/middleware/auth.ts` | JWT validation, optional vs required auth |
| `backend/api/src/lib/storageSign.ts` | Service role key exposure, path traversal in sign requests |
| `backend/api/src/db/` | SQL injection, missing ownership in queries |
| `backend/indexer/src/` | Path traversal from content root, command injection via probes |
| `backend/supabase/migrations/` | RLS disabled/missing, overly broad policies, `anon` grants |
| `apps/android/app/src/main/java/` | WebView/Intent leaks, cleartext, token storage, exported components |
| `apps/desktop/src/preload/` | Over-broad IPC bridge |
| `apps/desktop/src/main/` | Node integration, file paths, env secrets |
| `apps/desktop/src/renderer/` | XSS, unsafe HTML, token in localStorage |
| `docs/openapi.yaml` | Endpoints missing auth in contract vs implementation |

## AGENTS.md Forbidden (never introduce while fixing)

- Secrets in code or commits
- Direct HTTP to content volume without signed URL
- Server sync of music playback progress
- Bypassing RLS for convenience
- Logout / UI block on expired JWT when offline
- Desktop `app.quit()` on window close without explicit quit flag
- Synchronous JWT exp check on main thread at cold start without network

## Backend API checklist

```
- [ ] Mutating routes use requiredAuth; reads of user data are scoped by user id
- [ ] PUT /progress/* only for audiobooks; book ownership implied via user_id in query
- [ ] POST /downloads/sign: auth required; track_ids validated; paths from DB not client
- [ ] JWT verified with server secret; no alg-none; sub required
- [ ] SQL uses parameterized queries ($1, $2) — no string concat with user input
- [ ] SERVICE_ROLE_KEY only in server env — never in client bundles
- [ ] Storage sign: encode path segments; reject .. and absolute paths
- [ ] Errors to client are generic; no stack traces or internal URLs in production
- [ ] CORS not wildcard with credentials on sensitive deployments (note if intentional)
```

## Supabase / SQL checklist

```
- [ ] RLS enabled on user tables (audiobook_progress, profiles, avatars)
- [ ] Policies use auth.uid() for row ownership — not true for all rows
- [ ] No policy granting anon INSERT/UPDATE/DELETE on user data
- [ ] Storage buckets: public read only where intended; avatars/content policies explicit
- [ ] service_role used only server-side (API, indexer) — never in mobile/desktop clients
- [ ] Migrations do not drop RLS or grant dangerous defaults
```

## Indexer checklist

```
- [ ] CONTENT_ROOT joins are normalized; reject paths escaping root
- [ ] ffprobe/execFile uses fixed args — user filenames not passed as shell fragments
- [ ] DB credentials from env only
- [ ] No HTTP server exposing scan results without auth (indexer is batch worker)
```

## Android checklist

```
- [ ] Tokens in EncryptedSharedPreferences or DataStore — not plain SharedPreferences
- [ ] No secrets in strings.xml, BuildConfig from committed files, or logs
- [ ] Network: HTTPS for API; cleartext only if explicitly configured and justified
- [ ] Exported components minimized; intents filtered
- [ ] Avatar/upload: user-scoped storage paths; no path from untrusted input without validation
- [ ] Offline auth: expired JWT does not force logout (AGENTS.md)
- [ ] WebView (if any): no javascript enabled for untrusted content
```

## Desktop checklist

```
- [ ] preload exposes minimal IPC — no full fs, shell, or remote module to renderer
- [ ] contextIsolation true; nodeIntegration false in renderer
- [ ] Renderer does not hold service_role or long-lived secrets
- [ ] SQL queries parameterized (better-sqlite3)
- [ ] No dangerouslySetInnerHTML with user/catalog HTML without sanitization
- [ ] File paths from downloads resolved under allowed directories
- [ ] Tray/quit behavior unchanged unless fixing a security defect
```

## Common smells → fixes

| Smell | Fix direction |
|-------|----------------|
| `req.params.id` in SQL without user check | Add `user_id = $auth` or RLS-backed query |
| Client sends `storage_path` for download | Server resolves path from track_id only |
| `jwt.decode` without verify | Use `jwt.verify` with secret |
| `eval`, `Function`, dynamic require with user input | Remove or whitelist |
| `..` in storage or file paths | Normalize and reject traversal |
| Secret in `docker-compose.yml` committed | Move to `.env`; document in `.env.example` |
| Log `Authorization` header or tokens | Redact or remove |
| OpenAPI endpoint public but route requires auth | Align contract and implementation |

## Commands

```bash
make lint
make test
cd backend/api && npm test
cd backend/indexer && npm test
cd apps/desktop && npm test   # if configured
./gradlew testDebugUnitTest   # Android
```

## Secret scan (quick grep patterns)

Run in scope only — not a substitute for logic review:

```bash
# Examples — adjust path
rg -i "(password|secret|api_key|service_role|private_key)\\s*[=:]" --glob '!*lock*' <scope>
rg "Bearer [A-Za-z0-9._-]{20,}" <scope>
rg "eyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]*" <scope>
```

If matches are real secrets: rotate credentials, remove from history if committed, use env vars.
