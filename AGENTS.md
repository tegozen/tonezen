# Tonezen — Agent & Developer Guide

## Project Overview

Monorepo offline-first media player:

- **Android** — Kotlin, Jetpack Compose, Media3, Room
- **Desktop** — Electron, React, Vite, better-sqlite3
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

### API-first

1. Update `docs/openapi.yaml`
2. Add/update contract tests
3. Implement Edge Functions / clients

### Backend

- Indexer and API: pure functions + thin IO layer
- Do not mix audiobook progress sync with music local state
- **Domain modules:** split IO by API domain (`catalog`, `downloads`, `favorites`, `progress`, …) — one repository module per domain under `db/`, aligned with `routes/` and OpenAPI; avoid monolithic `ApiRepository` god objects
- Repositories: SQL/IO only; conflict resolution and other rules live in pure `lib/` helpers

### Sync (audiobooks)

- **Push:** Supabase Realtime `postgres_changes` on `audiobook_progress`, `favorites`, catalog tables
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
- UI strings: i18n-ready (externalized)

## Code Style

| Area | Tool | Rules |
|------|------|-------|
| Kotlin/Android | ktlint + detekt | Official Kotlin style; Compose in `ui/`; `suspend` for IO |
| TypeScript/React | ESLint + Prettier | strict TS; functional components; hooks for logic |
| SQL | pg formatter | snake_case; explicit RLS in migrations |
| Edge Functions (Deno) | deno lint/fmt | Modules < 200 lines; no `any` |
| Indexer (Node/TS) | ESLint + Prettier | Same as desktop |

## TDD (mandatory for business logic)

1. **Red** — write failing test or update OpenAPI spec
2. **Green** — minimal implementation
3. **Refactor** — without behavior change
4. CI must pass before merge

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
- [ ] CI green (lint → test → build)
- [ ] OpenAPI updated (if API changed)
- [ ] AGENTS.md rules followed

## Commands

```bash
docker compose up -d          # Start backend stack
make test                     # Run all unit tests
make lint                     # Run linters
```

See [`README.md`](README.md) for full setup.
