# 001 — Product and architecture

- Monorepo product: Android (Kotlin/Compose/Media3/Room), desktop (Electron/React/Vite/Tailwind/
  better-sqlite3), Express API, self-hosted Supabase, catalog indexer, and release landing page.
- Offline-first clients keep local catalog, downloads, playback state, and audiobook progress. Network
  recovery must not block startup or turn an expired offline JWT into logout.
- Shared content types are `audiobook` and `music`. Audiobook progress syncs; music progress is local only.
- Client dependency direction is `ui -> domain -> data`. Cross-platform rules belong in Android `domain/`
  and desktop `src/core/`; UI layers orchestrate and render.
- Desktop renderer follows Feature-Sliced Design. Electron main is split by domain and exits only through
  the explicit tray action; window close/minimize hides to tray.
- API domains, database repositories, routes, and `docs/openapi.yaml` stay aligned. SQL migrations are
  append-only once committed or applied.
- Russian UI copy is inline at usage sites. Android and desktop should remain behaviorally aligned where
  `docs/client-user-flows.md` does not declare a platform exception.
- Tests are optional and currently absent for Android and desktop. Agents write tests or run verification
  commands only when explicitly requested.
