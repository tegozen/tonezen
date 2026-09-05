# 004 — Backend, auth and deployment

- Self-hosted Supabase provides Auth, PostgreSQL, Realtime, Storage, and Studio behind the deployment
  gateway. Runtime state lives in persistent volumes; storage content may use the configured Beget S3.
- Express API owns authenticated catalog/download/progress/profile/book-watch endpoints. Database IO is
  split by domain; conflict resolution and validation belong to pure helpers.
- Auth startup is offline-safe: locally stored sessions render immediately, token refresh is coalesced and
  attempted only online, and offline expiry does not force logout. Password change validates the current
  password; profile/avatar updates synchronize across clients.
- Realtime tenant/schema bootstrap, storage migrations, health checks, first-run admin seed, and migration
  ownership were hardened during the initial production rollout.
- GlitchTip/Sentry-compatible crash reporting and symbol collection are integrated for production clients;
  release builds skip unavailable mapping uploads rather than failing incorrectly.
- Landing exposes recent release metadata and downloadable artifacts. Release skills build platform
  packages, copy artifacts to landing downloads, bump both clients, tag, and push only when explicitly
  invoked.
- Android release signing requires an explicit persistent keystore; git-ignored
  `apps/android/signing.properties` supports portable paths relative to the Android project, with
  environment/Gradle overrides. The silent debug-key fallback was removed after Docker-generated
  signing broke upgrades. The Windows key matching installed 0.21.0 was recovered locally; the
  rebuilt 0.23.0 APK passes `apksigner verify` with the same certificate. See README for its fingerprint.
- Current release line progressed from the initial 0.1.x production stack to 0.23.0. Exact environment and
  operating instructions remain in `README.md`, `.env.example`, Docker files, and release skills.
