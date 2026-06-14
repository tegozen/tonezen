# Tonezen — Agent & Developer Guide

## Project Overview

Monorepo offline-first media player:

- **Android** — Kotlin, Jetpack Compose, Media3, Room
- **Desktop** — Electron, React, Vite, Tailwind CSS, better-sqlite3
- **Backend** — Self-hosted Supabase (Auth, Realtime, Storage, Studio), catalog indexer

Shared API contract: [`docs/openapi.yaml`](docs/openapi.yaml).

Content types:

- `audiobook` — progress syncs for authenticated users
- `music` — local progress only, no server sync

## Architecture Rules

### Client layers

```
ui → domain → data
```

Dependencies point inward only. Domain logic must not import Android, Electron, or Supabase SDKs.

### Android (Kotlin) — mandatory structure

Based on [Google app architecture](https://developer.android.com/topic/architecture) and Kotlin conventions. Full checklist: [`.cursor/rules/kotlin-android.mdc`](.cursor/rules/kotlin-android.mdc).

**Layers**

| Package | Responsibility |
|---------|----------------|
| `ui/<feature>/` | Compose screens, feature ViewModel, `*UiState`, navigation |
| `domain/` | Pure models + rules (merge, session, playback coordination) |
| `data/local/` | Room entities, DAOs, DB, repositories, `EntityMappers` |
| `data/remote/<domain>/` | HTTP/Realtime per OpenAPI domain (`catalog`, `progress`, …) |
| `playback/` | Media3 service + client; no business rules |
| `di/` | Hilt modules |

**Hard rules**

- **One ViewModel per feature** — no god ViewModel for auth + library + player + downloads.
- **Unidirectional data flow:** `UiState` up, user events down; single source of truth in repository/ViewModel.
- **Repositories per API domain** — ViewModels depend on repos, never on `CatalogDao`, `ApiClient`, or `*Entity`.
- **Entity/DTO mapping only in `data/`** — `domain/` uses `Book`, `Track`, `AudiobookProgress`, not Room types.
- **Compose is dumb UI** — no repository calls, no content-type/sync branching, strings from `strings.xml`.
- **IO on background** — all Room/HTTP via `suspend` or `Flow`; no `while (true)` polling in ViewModel.
- **Modules ~≤200 lines** — split screens, DAOs, and API clients by feature/domain.
- **Domain tests first** for business logic (JUnit + coroutines-test; Turbine for Flow).

**Known debt (fix when touching the area)**

- Keep this section evidence-based and update it with exact files when new debt is found.
- Do not re-add stale items without verifying the current code first.

### API-first

1. Update `docs/openapi.yaml`
2. Add/update contract tests
3. Implement Edge Functions / clients

### Backend

- Indexer and API: pure functions + thin IO layer
- Do not mix audiobook progress sync with music local state
- **Domain modules:** split IO by API domain (`catalog`, `downloads`, `progress`, …) — one repository module per domain under `db/`, aligned with `routes/` and OpenAPI; avoid monolithic `ApiRepository` god objects
- Repositories: SQL/IO only; conflict resolution and other rules live in pure `lib/` helpers

### Sync (audiobooks)

- **Push:** Supabase Realtime `postgres_changes` on `audiobook_progress`, catalog tables
- **Pull:** REST on login / reconnect (`GET /progress/audiobooks`, catalog endpoints)
- **Conflict resolution:** last-write-wins by `updated_at`
- Local write always; server push when authenticated and online
- Music progress stays local only — no Realtime subscription

### Auth (offline-safe)

- Expired JWT while **offline** ≠ logout
- Refresh tokens **only when online**
- Never block UI or force logout on cold start without network

### Desktop lifecycle

- Close (X) and minimize → hide to system tray
- `app.quit()` only from tray context menu «Exit» with explicit quit flag

## Git Style

- **Conventional Commits:** `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `ci:`
- Optional scope: `feat(android):`, `fix(indexer):`
- Branches: `feat/…`, `fix/…`, `chore/…` from `main`
- Atomic commits: one feature + its tests per PR
- Commit messages and code comments: **English**
- UI strings: **Russian only** — externalized in `strings.xml` (Android) and `i18n/strings.ts` (desktop); no English user-facing copy in clients

## Code Style

| Area | Tool | Rules |
|------|------|-------|
| Kotlin/Android | `./gradlew testDebugUnitTest` (+ ktlint/detekt when configured) | See [kotlin-android.mdc](.cursor/rules/kotlin-android.mdc): feature ViewModels, UDF, repos per domain, pure `domain/`, Compose stateless, `strings.xml` for UI text |
| TypeScript/React | ESLint + Prettier | strict TS; functional components; hooks for logic; UI copy in `i18n/strings.ts` (**Russian only**) |
| Desktop renderer UI | Tailwind CSS v4 (`@tailwindcss/vite`) | utility classes + `@layer components` in `styles.css`; no inline `style` props |
| SQL | pg formatter | snake_case; explicit RLS in migrations |
| Edge Functions (Deno) | deno lint/fmt | Modules < 200 lines; no `any` |
| Indexer (Node/TS) | ESLint + Prettier | Same as desktop |

## TDD (mandatory for business logic)

1. **Red** — write failing test or update OpenAPI spec
2. **Green** — minimal implementation
3. **Refactor** — without behavior change
4. `make test` and `make lint` must pass before merge

PRs without tests for domain/sync/indexer/API changes are not merged.

## Forbidden

- Secrets in code or commits (use `.env.example` only)
- Direct HTTP access to content volume without signed URL
- Server sync of music playback progress
- Bypassing RLS for convenience
- Logout / UI block on expired JWT when offline
- Desktop `app.quit()` on window close without explicit quit flag
- Synchronous JWT exp check on main thread at cold start without network
- PRs > 400 lines without justification

## PR Checklist

- [ ] Tests added/updated
- [ ] `make lint` and `make test` green
- [ ] OpenAPI updated (if API changed)
- [ ] AGENTS.md rules followed

## Commands

```bash
docker compose up -d          # Start backend stack
make test                     # Run all unit tests
make lint                     # Run linters
```

See [`README.md`](README.md) for full setup.
