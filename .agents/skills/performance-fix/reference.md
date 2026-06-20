# Performance Fix Reference (Tonezen)

Stack-specific checks for [SKILL.md](SKILL.md). Read sections matching audit scope.

## Repo layout → performance surface

| Path | Primary bottlenecks |
|------|---------------------|
| `backend/api/src/db/` | N+1 queries, missing JOINs, full table scans |
| `backend/api/src/lib/storageSign.ts` | Serial signing; duplicate signs for same path |
| `backend/api/src/routes/` | Per-request work that should live in repo batch |
| `backend/indexer/src/db/` | Per-track ffprobe; per-row INSERT loops |
| `backend/indexer/src/scanner.ts` | Sequential fs + probe on large libraries |
| `apps/android/.../ui/` | Compose recomposition, image decode, list scroll |
| `apps/android/.../data/` | Room on main thread, missing indices, cold Flow chains |
| `apps/android/.../playback/` | Media3 buffer, service churn |
| `apps/desktop/src/renderer/` | React re-renders, large lists, image loading |
| `apps/desktop/src/main/` | Sync SQLite on IPC hot paths |

## AGENTS.md constraints (do not break while optimizing)

- IO on background — Room/HTTP via `suspend` or `Flow`
- No `while (true)` polling in ViewModel
- Expired JWT offline ≠ logout; no sync JWT exp on main thread at cold start without network
- Music progress local only — do not sync for “performance”
- Desktop: `app.quit()` only from tray — unrelated to perf unless fixing IPC flood

## Backend API checklist

```
- [ ] getCycles / getBookDetail: no query-per-row in loops — use JOIN or IN batch
- [ ] updated_since filters use indexed columns (updated_at)
- [ ] downloads/sign: dedupe storage paths before signing (see signStoragePaths)
- [ ] Pool connections not leaked; transactions bounded
- [ ] JSON payloads not over-fetched (select only needed columns)
- [ ] No sync file/network work in request handler beyond necessary await
```

Known smell pattern (`catalog.getCycles`): loop over cycles → query books per cycle. Fix: single query with JOIN + group in app, or two queries with `cycle_id IN (...)`.

## Indexer checklist

```
- [ ] analyzeAudioFile / ffprobe not rerun when checksum/size unchanged (if detectable)
- [ ] upsertCatalog: batch where PostgreSQL allows; avoid redundant UPDATEs
- [ ] Scanner: avoid double stat/read on same path
- [ ] sha256 only when needed for change detection — full hash is expensive on large files
- [ ] setInterval scan interval respects INDEXER_INTERVAL_SECONDS — no accidental tight loop
```

## Android checklist

```
- [ ] ViewModel: viewModelScope + dispatcher.IO for repo calls
- [ ] No blocking get() on main thread (runBlocking, .execute())
- [ ] StateFlow/Flow for library/player — no Timer or while(true) poll
- [ ] Room: @Transaction for multi-table reads; Flow for observable queries
- [ ] LazyColumn: stable keys, contentType; avoid heavy work in item lambda
- [ ] Images: appropriate size decode (Coil/size constraints); not full-res in lists
- [ ] remember / derivedStateOf for expensive derived UI state
- [ ] collect flows with lifecycle-aware APIs (repeatOnLifecycle, collectAsStateWithLifecycle)
- [ ] Cold start: no network-blocking JWT refresh before first paint
```

## Desktop checklist

```
- [ ] React: memo on heavy list rows; stable callbacks (useCallback) where lists re-render
- [ ] Virtualize long library lists (windowing) if rendering all rows
- [ ] Debounce search input before catalog query
- [ ] SQLite: prepared statements; indexes on filter/sort columns; WAL mode if applicable
- [ ] IPC: batch reads; avoid main↔renderer round-trips per row
- [ ] contextIsolation — keep heavy work in main process with async IPC results
```

## Compose / React smells → fixes

| Smell | Fix direction |
|-------|----------------|
| Whole library in one UiState object | Split state; hoist list slice |
| `Modifier` chain recreated causing full list recompose | Stable modifiers; keys |
| `items(list)` without `key` | `items(list, key = { it.id })` |
| Fetch on every composition | LaunchedEffect / useEffect with deps |
| Mapping entire DB to domain on every emission | map in flow on background; cache if pure |

## SQL / Room smells → fixes

| Smell | Fix direction |
|-------|----------------|
| N+1 in loop | JOIN, `@Relation`, or batch IN query |
| `SELECT *` on wide tables | Project columns |
| Missing index on `updated_at`, `user_id`, foreign keys | Migration with index |
| Loading all progress rows when user has few updates | Filter + pagination |

## Commands

```bash
make lint
make test
cd backend/api && npm test
cd backend/indexer && npm test
./gradlew testDebugUnitTest
```

## Quick inspection (not a substitute for reading hot paths)

```bash
# Polling loops in Android UI layer
rg "while\\s*\\(\\s*true" apps/android --glob '*.kt'

# runBlocking in Android (often main-thread risk)
rg "runBlocking" apps/android --glob '*.kt'

# Per-row query patterns in backend db
rg "for.*rows" backend/api/src/db --glob '*.ts'
```

Remove temporary profiling logs before finishing unless the user wants them kept.
