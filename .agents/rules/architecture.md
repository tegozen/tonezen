# Architecture

Always apply these rules.

- Client layers: `ui → domain → data` (dependencies inward only).
- Domain has no platform SDK imports.
- Android uses feature ViewModels, repositories per OpenAPI domain, and UDF; see [kotlin-android.md](kotlin-android.md).
- API changes start in `docs/openapi.yaml`, then code. Add tests only when explicitly requested.
- Offline-first: local cache plus synchronization when online.
- Client playback UX follows [`docs/client-user-flows.md`](../../docs/client-user-flows.md).
- Audiobook progress syncs; music progress is local only.
- Expired JWT while offline does not log the user out; refresh only when online.
- Desktop close/minimize hides to tray; quit only through the tray menu.
