# Android (Kotlin)

Apply under `apps/android/**`.

Stack: Kotlin, Jetpack Compose, Hilt, Room, Media3, OkHttp, and Coroutines/Flow.

Follow Google app architecture: `UI → domain → data`, unidirectional data flow, and a single source of truth.

## Package layout

```
com.tonezen.app/
  ui/           # Composables, ViewModels, UiState — one feature per subpackage
  domain/       # Pure Kotlin models and rules — no android.* imports
  data/
    local/      # Room entities, DAOs, database, repositories, entity mappers
    remote/     # HTTP/Realtime clients split by OpenAPI domain
    network/    # Connectivity and transport helpers
  playback/     # Media3 service and client bridge
  di/           # Hilt modules only
```

Repositories align with OpenAPI domains such as catalog, downloads, progress, and auth.

## Layer rules

| Layer | May import | Must not import |
|---|---|---|
| `ui/` | domain models, repository interfaces or injected repositories | `CatalogDao`, Room, raw OkHttp, entity types |
| `domain/` | Kotlin standard library | `android.*`, `androidx.*`, Compose, Room, OkHttp, Supabase |
| `data/` | domain, Room, OkHttp, platform SDKs | Compose, ViewModels |
| `playback/` | domain, Media3 | business rules that belong in a domain coordinator |

Map entities and DTOs to domain models only in `data/`.

## ViewModel

- Use one ViewModel per feature, not a god ViewModel.
- Expose `StateFlow<FeatureUiState>`; collect with `collectAsStateWithLifecycle()`.
- UI sends events to the ViewModel; the ViewModel calls repositories/use cases and updates `UiState`.
- ViewModels orchestrate only: no JSON parsing, SQL, or merge/conflict rules.
- Inject repositories, not DAOs or API clients.
- Avoid `@ApplicationContext Context`; inject focused abstractions such as `NetworkMonitor`.
- Do not poll with `while (true)`; use repository Flow, `callbackFlow`, or `PlaybackClient.snapshot`.
- Run mutations in `viewModelScope`; never use `GlobalScope`.

## Compose

- Screens are stateless: `Screen(uiState, onEvent)`.
- Hoist durable state; Composables hold only ephemeral UI state.
- Keep business rules and repository calls out of `@Composable`.
- Put Russian user-facing strings inline at usage sites.
- Keep files around 200 lines or less; extract feature-local components.
- Use theme tokens; avoid magic colors in screens.
- Follow [Navigation Compose rules](navigation-compose.md) for routes, back stacks, and overlays.

## Data and Room

- Keep entities, DAOs, and the database in separate files.
- DAO methods return entities; repositories expose domain types or `Flow<List<...>>`.
- Database and network IO is always `suspend` or `Flow`; never block the main thread.
- Write locally first and synchronize online; only audiobook progress syncs.
- Do not inject DAOs outside `data/`.

## Coroutines and Flow

- Repositories return Flow for observable data and suspend functions for one-shot commands.
- Use `stateIn` or `shareIn` when multiple collectors consume the same stream.
- When tests are requested, prefer `runTest`, Turbine, and pure domain tests without Robolectric.

## Dependency injection

- Use constructor `@Inject` on repositories and ViewModels.
- Use `@Singleton` for repositories and clients.
- Bind repository interfaces in `di/` when test fakes are needed.

## Playback, auth, and sync

- Use `MediaSessionService` for background audio; UI communicates through `PlaybackClient`.
- Playback rules live in `domain/playback/` and must be wired into production behavior.
- Expired JWT while offline does not log the user out; refresh only online.
- Audiobook progress writes locally and pushes online; music progress remains local.
- Progress conflict rules live in `domain/progress/`; transport lives in `data/remote/progress/`.

## Forbidden

- God ViewModels, API clients, or DAOs.
- Platform, Room, or entity imports in `domain/`.
- DAO or entity dependencies in ViewModels.
- English user-facing client copy.
- Synchronous network/database work on the main thread.
- Server synchronization of music progress.
- Forced logout for an expired JWT while offline.

## Verification

Run only when the user explicitly requests verification:

```bash
cd apps/android && ./gradlew assembleDebug
rg "import android\.|import androidx\.|CatalogDao|BookEntity" apps/android/app/src/main/java/com/tonezen/app/domain/
rg "CatalogDao" apps/android/app/src/main/java/com/tonezen/app/ui/
rg "DownloadQueueDao|CatalogDao" apps/android/app/src/main/java/com/tonezen/app/playback/
```

Android has no unit-test suite.
