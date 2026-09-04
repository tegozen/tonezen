# 003 — Catalog, downloads and indexer

- The indexer scans Supabase/S3 content incrementally, skips unchanged media probes, parses audiobook
  cycles and flat music metadata, and reconciles removed tracks/books/cycles with soft deletion.
- Audiobook storage slugs are unique and display names are preserved separately from safe/transliterated
  paths. Track order survives partial scans and is naturally reconciled after incremental uploads.
- Catalog changes reach clients through Realtime plus REST recovery. Startup and reconnect flows must
  tolerate missed subscriptions and expired-token recovery.
- Content volume is private. Clients download only through signed URLs; URL origin/path validation and
  byte-range forwarding protect both access and local playback.
- Android and desktop use persistent prioritized download queues with retry/resume behavior, per-track and
  bulk actions, progress UI, stale-state reconciliation, and safe `.part` promotion. Existing files finish
  queue items instead of causing redownload loops.
- “Download all” and playback-triggered download use the same queue semantics for audiobooks and music.
  Offline lists expose completed local files and skip unavailable tracks.
- When the indexer inserts or restores a cycle, it provisions missing book-watch configuration for every
  existing user in the same transaction without overwriting customized search queries.
