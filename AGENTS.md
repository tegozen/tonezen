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

### Client UX flows (mandatory)

Before changing library, playback, downloads, or progress UI/logic on **Android** or **Desktop**, read [`docs/client-user-flows.md`](docs/client-user-flows.md).

- Treat it as the source of truth for user-visible behavior.
- Android and Desktop must stay behaviorally aligned unless the doc explicitly allows a platform exception.
- When fixing a bug or adding a feature in these areas, update the doc if behavior changes.
- Prefer implementing rules in `domain/` (Android) and `apps/desktop/src/shared/` (Desktop); UI only orchestrates.

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
- **Compose is dumb UI** — no repository calls, no content-type/sync branching; UI copy inline at usage sites (**Russian only**).
- **IO on background** — all Room/HTTP via `suspend` or `Flow`; no `while (true)` polling in ViewModel.
- **Modules ~≤200 lines** — split screens, DAOs, and API clients by feature/domain.
- **Domain tests** for business logic (JUnit + coroutines-test; Turbine for Flow) — only when the user explicitly asks for tests.

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
- **SQL migrations** (`backend/supabase/migrations/`): append-only — never edit a migration file that is already committed or applied (tracked in `schema_migrations`). Schema fixes and new behavior require a new numbered migration (e.g. `017_…sql`).

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

## Agent Skills

- Create shared skills under `.agents/skills/<skill-name>/SKILL.md` so Codex and Cursor can both discover them.
- Do not add new skills under agent-specific directories such as `.cursor/skills` or `.codex/skills` unless the user explicitly asks for that location.

## Agent execution policy

- Do **not** write or update tests unless the user explicitly asks for tests.
- Do **not** run commands (`make test`, `make lint`, Gradle, npm, docker, builds, apps, etc.) unless the user explicitly asks to run them.
- Implement code changes only; leave verification and test authoring to an explicit user request.

## Git Style

- **Conventional Commits:** `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `ci:`
- Optional scope: `feat(android):`, `fix(indexer):`
- Branches: `feat/…`, `fix/…`, `chore/…` from `main`
- Codex/local chat workflow: do **not** create or switch to a new branch or git worktree
  unless the user explicitly asks for it. Work in the current checkout/branch by default.
- Atomic commits: one feature + its tests per PR
- Commit messages and code comments: **English**
- UI strings: **Russian only** — inline at usage sites (Android Compose and Desktop); no English user-facing copy in clients

## Code Style

| Area | Tool | Rules |
|------|------|-------|
| Kotlin/Android | `./gradlew testDebugUnitTest` (+ ktlint/detekt when configured) | See [kotlin-android.mdc](.cursor/rules/kotlin-android.mdc): feature ViewModels, UDF, repos per domain, pure `domain/`, Compose stateless, inline Russian UI copy |
| TypeScript/React | ESLint + Prettier | strict TS; functional components; hooks for logic; UI copy inline at usage sites (**Russian only**) |
| Desktop renderer UI | Tailwind CSS v4 (`@tailwindcss/vite`) | utility classes + `@layer components` in `styles.css`; no inline `style` props |
| SQL | pg formatter | snake_case; explicit RLS in migrations |
| Edge Functions (Deno) | deno lint/fmt | Modules < 200 lines; no `any` |
| Indexer (Node/TS) | ESLint + Prettier | Same as desktop |

## TDD (when the user asks for tests / before merge)

Applies only when the user explicitly requests tests or when preparing a mergeable PR after such a request:

1. **Red** — write failing test or update OpenAPI spec
2. **Green** — minimal implementation
3. **Refactor** — without behavior change
4. `make test` and `make lint` must pass before merge

PRs without tests for domain/sync/indexer/API changes are not merged.

## Forbidden

- Secrets in code or commits (use `.env.example` only)
- Editing committed or applied SQL migrations in `backend/supabase/migrations/` — add a new migration instead
- Direct HTTP access to content volume without signed URL
- Server sync of music playback progress
- Bypassing RLS for convenience
- Logout / UI block on expired JWT when offline
- Desktop `app.quit()` on window close without explicit quit flag
- Synchronous JWT exp check on main thread at cold start without network
- PRs > 400 lines without justification
- Writing/updating tests or running any commands without an explicit user request (see Agent execution policy)

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
