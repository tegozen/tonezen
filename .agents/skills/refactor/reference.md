# Refactor Reference (Tonezen)

Stack-specific guidance for [SKILL.md](SKILL.md). Read the section that matches scope.

## Repo layout

| Path | Stack | Layer notes |
|------|-------|-------------|
| `apps/android/app/src/main/java/com/tonezen/app/ui/` | Compose UI | Screens, ViewModels; no direct DB/network |
| `apps/android/app/src/main/java/com/tonezen/app/domain/` | Domain | Pure Kotlin; no Android/Supabase imports |
| `apps/android/app/src/main/java/com/tonezen/app/data/` | Data | Room, API clients, repositories |
| `apps/android/app/src/main/java/com/tonezen/app/playback/` | Platform playback | Media3 service; bridge to domain |
| `apps/android/app/src/main/java/com/tonezen/app/di/` | DI | Hilt modules wiring layers |
| `apps/desktop/src/renderer/` | React UI | Components; logic in hooks or `shared/` |
| `apps/desktop/src/main/` | Electron main | Window, tray, IPC, SQLite, sync IO |
| `apps/desktop/src/preload/` | Preload | Narrow IPC bridge |
| `apps/desktop/src/shared/` | Shared TS | Pure helpers usable from main + renderer |
| `backend/api/src/` | Express API | Routes thin; logic in `lib/` pure fns |
| `backend/indexer/src/` | Indexer | `scanner`, `parsers` pure; `db` IO thin |
| `backend/supabase/migrations/` | SQL | snake_case, explicit RLS |
| `docs/openapi.yaml` | Contract | Source of truth for REST API |

## SOLID in this codebase

| Principle | Tonezen application |
|-----------|---------------------|
| **S** — Single responsibility | One module = one reason to change (e.g. `ProgressMerger` merges only; sync transport lives in data) |
| **O** — Open/closed | Extend via new repository impl or strategy, not `if (platform)` in domain |
| **L** — Liskov | Substitutable fakes in tests; repository interfaces honored by all impls |
| **I** — Interface segregation | Small repository/protocol surfaces; avoid god-interfaces |
| **D** — Dependency inversion | Domain depends on abstractions; data implements them; UI depends on domain |

## Common smells → actions

| Smell | Likely fix | Layer target |
|-------|------------|--------------|
| UI file calls Supabase/SQLite directly | Move to repository in `data/`; UI calls ViewModel/use-case | `data` |
| Domain imports `android.*`, `electron`, `@supabase/*` | Extract platform code to `data/` or `main/` | stay out of `domain` |
| God class / 300+ line module | Split by responsibility within same layer | same layer, new files |
| God Android ViewModel (auth+library+player) | Split into feature ViewModels under `ui/<feature>/` | `ui/` |
| ViewModel injects DAO or ApiClient | Add repository in `data/`; map entities there | `data/local` or `data/remote` |
| Room entity in domain merge logic | Domain type + mapper in `data/` | `domain/` + `data/` |
| Compose calls repository or parses JSON | Move to ViewModel; pass UiState + events | `ui/` |
| Duplicated merge/sync logic Android + Desktop | Extract to pure fn; keep separate IO wrappers | `domain` or `shared/` |
| Mixed pure logic + HTTP/FS in one fn | Extract pure fn; leave IO in caller | pure → `lib/` / `domain/` |
| Business rules in route handler | Move to `lib/`; handler wires request/response | `backend/api/src/lib/` |
| Audiobook and music progress intertwined | Split paths; music never syncs | separate modules |
| Hardcoded UI strings in logic | Move to i18n resources / string table | `ui` / renderer |

## Android checklist

Full rules: [`.cursor/rules/kotlin-android.mdc`](../../rules/kotlin-android.mdc). Sources: [Google app architecture](https://developer.android.com/topic/architecture), [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html), [Compose UI state](https://developer.android.com/jetpack/compose/state).

### Layer boundaries

```
- [ ] domain/ — zero imports of android.*, androidx.*, Room, OkHttp, data.local.*
- [ ] ui/ — no CatalogDao, ApiClient, *Entity; only repositories + domain models
- [ ] data/ — entity↔domain mapping only here (EntityMappers or per-domain mappers)
- [ ] playback/ — Media3 only; rules in domain/playback/
```

### ViewModel & UDF

```
- [ ] One ViewModel per feature (not god MainViewModel)
- [ ] StateFlow<FeatureUiState> exposed; events as methods (onXClicked)
- [ ] No Context in ViewModel — use NetworkMonitor or repo
- [ ] No while(true) polling — Flow from repo or PlaybackClient
- [ ] viewModelScope for coroutines; no blocking IO
- [ ] Pure rules moved to domain/; IO moved to data/
```

### Compose

```
- [ ] Screen(uiState, onEvent) — stateless, no repo/DAO calls
- [ ] No business branching (content type, sync, auth) in @Composable
- [ ] User strings in strings.xml, not ViewModel string concat
- [ ] File ≤ ~200 lines; split sub-composables to components/ or feature package
```

### Data & repositories

```
- [ ] One repository per OpenAPI domain (catalog, progress, downloads, auth)
- [ ] CatalogDao used only inside data/local/ repositories
- [ ] Room: separate entity, DAO, Database files
- [ ] ApiClient split by domain (not one 150+ line god client)
- [ ] suspend for commands, Flow for observable lists/progress
- [ ] Offline-first: local write then conditional remote push (audiobooks only)
```

### Smells → refactor action (Android-specific)

| Smell | Fix |
|-------|-----|
| ViewModel injects `CatalogDao` | Introduce/extend `*Repository`; map entities inside repo |
| `domain/` imports `*Entity` | Merge/map on `AudiobookProgress` etc. in `data/` only |
| God `MainViewModel` | Split `ui/auth/`, `ui/library/`, `ui/player/` each with ViewModel + UiState |
| Monolithic `ApiClient` | `data/remote/catalog/`, `progress/`, `downloads/` clients |
| Entities + DAO + DB in one file | `entity/BookEntity.kt`, `dao/CatalogDao.kt`, `TonezenDatabase.kt` |
| Tested domain never called | Wire in separate feat PR, or leave untouched in refactor-only pass |
| Hardcoded `"Continue: …"` in VM | `strings.xml` + format arg in Composable |
| `getTrackEntitiesForBook` in repo API | Return domain `Track`; hide entities inside repo |
| `SessionManager()` default in repo ctor | Inject via Hilt only |

### Target package shape (after refactor)

```
ui/library/     LibraryScreen.kt  LibraryViewModel.kt  LibraryUiState.kt
ui/player/      BookDetailScreen.kt PlayerViewModel.kt  PlayerUiState.kt
ui/auth/        AuthScreen.kt     AuthViewModel.kt     AuthUiState.kt
data/local/     CatalogRepository.kt  ProgressRepository.kt  EntityMappers.kt
data/remote/    catalog/ progress/ downloads/ auth/
domain/         model/ session/ progress/ playback/
```

### Verify

```bash
cd apps/android && ./gradlew assembleDebug

# Layer grep — should return no matches
rg "import android\.|import androidx\.|data\.local\." apps/android/app/src/main/java/com/tonezen/app/domain/
rg "CatalogDao|BookEntity|TrackEntity" apps/android/app/src/main/java/com/tonezen/app/ui/
```

Prefer moving **pure** logic ViewModel → `domain/`; **IO** ViewModel → `data/` repository; **UI state** → feature `*UiState` + stateless Composable.

## Desktop checklist

```
- [ ] renderer/ — React components + hooks only; no direct Node/Electron APIs
- [ ] renderer styling — Tailwind utilities / `@layer components` in `styles.css`; no inline `style` props
- [ ] main/ — Electron lifecycle, tray, IPC handlers, better-sqlite3
- [ ] shared/ — pure TS (progress merge, session types, cycle playback)
- [ ] preload/ — minimal surface; no business logic
- [ ] Close/minimize → tray; app.quit() only from tray Exit + quit flag
- [ ] JWT refresh only when online; offline exp ≠ logout
- [ ] IPC contract unchanged unless user asked for API change
```

**Lint / test:**

```bash
cd apps/desktop && npm run lint && npm test
```

When splitting `main/` modules: keep side effects at edges; pure helpers → `shared/`.

## Backend API checklist

```
- [ ] Handlers in app.ts stay thin (< ~20 lines per route ideally)
- [ ] Pure logic in src/lib/ — testable without HTTP
- [ ] auth middleware unchanged behavior unless scoped
- [ ] Signed URLs for content; no direct volume HTTP
- [ ] Refactor does NOT change openapi.yaml unless user asked
```

**Lint / test:**

```bash
cd backend/api && npm run lint && npm test
```

## Indexer checklist

```
- [ ] scanner.ts / parsers.ts — pure functions, no hidden IO
- [ ] db.ts / index.ts — thin IO orchestration
- [ ] Modules stay < 200 lines where possible (AGENTS.md)
- [ ] No any in TypeScript
```

**Lint / test:**

```bash
cd backend/indexer && npm run lint && npm test
```

## Supabase / SQL refactor

```
- [ ] snake_case columns and tables
- [ ] RLS policies explicit — never remove/bypass for convenience
- [ ] Migrations are additive when possible; behavior change = new migration
- [ ] Realtime publication tables unchanged unless scoped
```

Refactoring SQL is high-risk: run related API/indexer tests after changes.

## Sync & auth (do not break)

| Area | Rule |
|------|------|
| Audiobook progress | Local write always; push when online + authenticated; Realtime push/pull |
| Music progress | Local only — no Realtime, no REST sync |
| Conflict resolution | last-write-wins by `updated_at` |
| Offline JWT | Expired token offline ≠ logout; no sync refresh check on cold start main thread |

When refactoring sync code, trace both content types separately before merging abstractions.

## Verification matrix

| Scope | Commands |
|-------|----------|
| Whole repo | `make lint` && `make test` |
| Android only | `./gradlew assembleDebug` in `apps/android` when the user asks (+ ktlint/detekt when configured); no unit-test suite |
| Desktop only | `npm run lint && npm test` in `apps/desktop` |
| API only | `npm run lint && npm test` in `backend/api` |
| Indexer only | `npm run lint && npm test` in `backend/indexer` |
| Domain logic (any) | Run package tests + grep for forbidden imports |

**Forbidden import grep (domain purity):**

```bash
# Android domain — should return no matches
rg "import android\.|import androidx\.|supabase" apps/android/app/src/main/java/com/tonezen/app/domain/
```

## Refactor sizing

| Size | Guidance |
|------|----------|
| < 100 lines | Single pass OK |
| 100–400 lines | One logical refactor; list files upfront |
| > 400 lines | Split into steps; confirm with user between steps |

## Rename / move checklist

```
- [ ] Update all imports (IDE or rg for old symbol/path)
- [ ] Update test imports and module mocks
- [ ] Update DI modules (Hilt AppModule, desktop IPC if applicable)
- [ ] No orphaned files
- [ ] Git shows only scope-related paths
```

Public API renames (exported types, IPC channel names, OpenAPI paths) require explicit user approval — treat as behavior change.
