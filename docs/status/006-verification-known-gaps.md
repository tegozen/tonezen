# 006 — Verification and known gaps

Last status update: 2026-09-05.

- Current declared client version is 0.22.0.
- Android and desktop intentionally have no unit-test suites. Backend and landing tests remain available
  but were not run for the current worktree.
- Android `assembleDebug` passed in the documented WSL Docker build environment after the clean-code
  refactor. Lint, runtime, device, offline, backend and end-to-end verification were not performed.
- The book-watch correction still requires an applied environment with migration `052_book_watch.sql`;
  indexer provisioning relies on those tables already existing.
- Thin modules listed under “Known debt” in `AGENTS.md` are accepted current exceptions. Recheck actual
  line counts before adding or removing debt.
- Platform parity is an ongoing constraint, not proof that every Android behavior currently has an exact
  desktop counterpart. Book watch is currently documented as Android behavior.
