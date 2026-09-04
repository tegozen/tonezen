# 006 — Verification and known gaps

Last status update: 2026-09-04.

- Current declared client version is 0.22.0.
- Android and desktop intentionally have no unit-test suites. Backend and landing tests remain available
  but are not mandatory and were not run for the current uncommitted book-watch correction.
- No build, lint, runtime, device, offline, or end-to-end verification was performed for the current
  handoff/status documentation change. Do not describe the worktree as verified until explicitly checked.
- The book-watch correction still requires an applied environment with migration `052_book_watch.sql`;
  indexer provisioning relies on those tables already existing.
- Thin modules listed under “Known debt” in `AGENTS.md` are accepted current exceptions. Recheck actual
  line counts before adding or removing debt.
- Platform parity is an ongoing constraint, not proof that every Android behavior currently has an exact
  desktop counterpart. Book watch is currently documented as Android behavior.
