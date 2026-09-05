# 006 — Verification and known gaps

Last status update: 2026-09-05.

- Current declared client version is 0.24.0 (Android code 45).
- Android `assembleRelease` and native Windows `dist:win` passed for 0.24.0. APK signature matches
  the previous distributed 0.23.0 APK. Both installers and crash symbols were copied to landing downloads;
  tag `v0.24.0` was pushed and verified on origin. Device/UI verification and production upload were not run.
- Android and desktop intentionally have no unit-test suites. Backend and landing tests remain available
  but were not run for the current worktree.
- Android `assembleDebug` passed in the documented WSL Docker build environment after the clean-code
  refactor. Lint, runtime, device, offline, backend and end-to-end verification were not performed.
- Android 0.23.0 `assembleRelease` passed in WSL Docker after restoring the persistent signing key.
  Android SDK `apksigner verify --print-certs` passed for the supplied 0.21.0 APK and rebuilt 0.23.0
  with identical signer SHA-256 fingerprints. Installation over 0.21.0 on the phone remains unverified.
- The book-watch correction still requires an applied environment with migration `052_book_watch.sql`;
  indexer provisioning relies on those tables already existing.
- Book-watch Docker verification passed after migration 057 and API defaults correction: seeds, login,
  snapshot, rename/query persistence, enqueue deduplication, and real worker completion (3 books / 5 links).
  Production application is pending; fresh-bootstrap Storage ordering needs the workaround in status 004.
- Thin modules listed under “Known debt” in `AGENTS.md` are accepted current exceptions. Recheck actual
  line counts before adding or removing debt.
- Platform parity is an ongoing constraint, not proof that every Android behavior currently has an exact
  desktop counterpart. Book watch is currently documented as Android behavior.
