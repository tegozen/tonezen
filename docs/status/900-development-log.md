# 900 — Development log

Newest first. This is a concise milestone history reconstructed from repository commits; it records every
major development stage, not every fix or release-only version bump. Prune superseded details when current
state changes, while retaining the architectural reason a stage mattered.

- 2026-09-05: Reproduced book-watch HTTP 500 in Docker (missing tonezen_api grants); added migration 057
  and corrected default-query seeding to preserve edits. Verified seeds twice, authenticated rename/read,
  validation/auth rejection, enqueue deduplication, and real worker completion with 3 books / 5 links.
  Scoped API Docker context; preserved production env and used local file Storage. Android save-path
  hardening was not rebuilt. Production deployment and fresh-bootstrap ordering repair remain pending.
- 2026-09-05: Released client 0.24.0 (Android code 45), migration 056, and tag v0.24.0 on origin.
  Android release and native Windows builds passed; APK signing matches distributed 0.23.0. Installers
  and crash symbols copied to landing downloads. Device checks and production upload remain pending.
- 2026-09-05: Added thin top/bottom separators without side borders or clipping to Android cycle book rows, with 12 dp
  spacing between cards. Builds and device checks were not run.
- 2026-09-05: Removed both Continue elements from Android cycle book rows and combined percentage,
  chapter, and time into one line with chapter truncation. Builds and device checks were not run.
- 2026-09-05: Restyled Android new-book events with the shared profile header, wrapping teal filters,
  contextual empty states, event cards, and mini-player clearance. Builds and device checks were not run.
- 2026-09-05: Fixed Android book-watch settings silently failing to open when the local watch was absent:
  open the editable form immediately using cached settings or cycle-title defaults, with no network
  prerequisite. Resolve the server record only on save; preserve input on save failure.
  Builds and tests were not run; device verification remains pending.
- 2026-09-05: Restored Android update compatibility using the original Windows signing key, removed
  release debug-key fallback, and documented persistent local signing configuration. Rebuilt 0.23.0
  successfully in WSL Docker; `apksigner` verified both old/new APKs with identical certificates.
  Copied the APK to local landing downloads and Windows Downloads; phone installation remains unverified.
- 2026-09-05: Refactored Android progress synchronization, book-detail playback, cycle progress/playback
  preparation, and nearby session lifecycle into focused components; removed Room entities from Book Watch
  UI and playback download queues without changing client contracts; verified the result with an Android
  debug build in the WSL Docker environment.
- 2026-09-04: Added Navigation Compose typed routing and state-preserving navigation, improved active
  download progress and cycle listened indicators, released 0.22.0, centralized shared agent rules/hooks,
  corrected automatic indexer-owned book-watch provisioning, and introduced this progressive handoff.
- 2026-08-31–09-02: Added Android book watch with backend worker, provider parsing, offline event history
  and notifications; hardened query validation/token delegation, added mojibake repair, refined Media3
  controller commands, and released 0.21.0.
- 2026-07-27: Added Android Nearby peer-to-peer audiobook progress synchronization, stabilized revision
  handling and Media3 binding, integrated GlitchTip crash reporting/symbol upload, and released 0.17.0
  through 0.20.0.
- 2026-07-26: Reworked audiobook progress hydration, conflict detection, persistence, and reconnect sync;
  added catalog deletion reconciliation and local media range serving; moved indexer HTTP concerns into
  the Express API; completed broad Android and desktop modularization; enforced desktop FSD; removed both
  client test suites by project policy; and advanced releases from 0.6.0 through 0.16.0.
- 2026-06-25: Wrote the authoritative cross-client user-flow specification; aligned splash bootstrap,
  Russian inline UI copy, audiobook/cycle resume, earlier-book confirmation, next-track prefetch, offline
  advance, music wave playback, and bulk download behavior; released 0.4.0 through 0.5.4.
- 2026-06-21–24: Added branded installers, release artifact publishing, safe/transliterated storage paths,
  incremental audiobook order preservation, macOS tray/config fixes, track downloads and book-detail UX,
  catalog pagination/rate limiting, and releases 0.1.11 through 0.3.0.
- 2026-06-16–20: Built persistent cross-platform download queues with retry/resume and Downloads UI;
  hardened signed URLs, offline catalogs, JWT/Realtime recovery, storage bootstrap and migrations; added
  music shuffle/My Wave, waveform/spectrum visuals, incremental media probing, progress conflicts, landing,
  Windows portable builds, and releases 0.1.4 through 0.1.10.
- 2026-06-13–15: Added cycle navigation/playback, avatar crop/upload, desktop parity, per-domain Android
  remote APIs, backend security/performance hardening, audiobook resume fixes, and the Beget S3-backed
  storage deployment.
- 2026-06-12: Created the original TPlayer monorepo, then renamed it Tonezen; established Android,
  Electron, Supabase, Auth, Storage, Realtime, signed downloads, cross-device audiobook progress, Media3
  and desktop media sessions, flat music/audiobook indexing, the Russian brandbook UI, layered Android
  architecture, modular API repositories, and initial production Docker setup.
