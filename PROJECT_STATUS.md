# Tonezen — project status

Last updated: 2026-09-05. Current client version: 0.24.0 (release builds pending).

Tonezen is an offline-first audiobook and music player with Android and Electron clients, a self-hosted
Supabase stack, an Express API, and a catalog indexer. Audiobook progress synchronizes between devices;
music progress remains local. The current worktree corrects automatic book-watch provisioning for every
indexed cycle and refactors Android client boundaries without changing behavior.

This is the session barrel. Read it completely, then open only the topic relevant to the task:

- [001 — Product and architecture](docs/status/001-product-architecture.md)
- [002 — Clients, playback and progress](docs/status/002-clients-playback-progress.md)
- [003 — Catalog, downloads and indexer](docs/status/003-catalog-downloads-indexer.md)
- [004 — Backend, auth and deployment](docs/status/004-backend-auth-deployment.md)
- [005 — Book watch](docs/status/005-book-watch.md)
- [006 — Verification and known gaps](docs/status/006-verification-known-gaps.md)
- [900 — Development log](docs/status/900-development-log.md)

Authoritative contracts remain in `docs/openapi.yaml` and `docs/client-user-flows.md`. Source is
authoritative for implementation details. Never infer that an unlisted check or platform scenario passed.
