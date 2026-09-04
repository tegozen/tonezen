# 002 — Clients, playback and progress

- Android uses Navigation Compose with typed routes. Bottom tabs preserve state; full-screen pages are
  destinations and sheets/dialogs/expanded player remain transient overlays.
- Both clients implement library, music, downloads, profile, book detail, cycle playback, persistent
  download state, and system media controls. UI behavior is specified in `docs/client-user-flows.md`.
- Audiobook resume is based on saved chapter/position. Cycle playback can continue across books, prompts
  before moving to an earlier cycle book where required, and stops rather than streaming a missing next
  track while offline.
- Chapter completion, explicit listened state, prefetch, queue advance, and full-cycle progress have been
  repeatedly aligned across Android and desktop. Android displays listened percentage per cycle book.
- Audiobook progress writes locally first and synchronizes using last-write-wins timestamps/revisions.
  Cold start hydrates server state before unsafe pushes, reconnect/window-show triggers refresh, and
  conflicts are surfaced rather than silently losing progress.
- Android supports nearby peer-to-peer audiobook progress exchange. Merge rules remain in pure domain
  code and must not mix music progress into server or peer synchronization.
- Music provides shuffled looping playback, “My Wave”, lazy queues, offline filtering, per-track and bulk
  downloads, and local-only progress. Download/play identifiers must resolve to canonical catalog book IDs.
- Android playback uses Media3 service/session; desktop integrates OS media controls. Queue commands are
  gated by media type, and background service binding must not unnecessarily start foreground playback.
