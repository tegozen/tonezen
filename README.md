# TPlayer

Offline-first cross-platform media player for audiobooks and music.

- **Android** — Kotlin, Jetpack Compose, Media3, Room
- **Desktop** — Electron, React (Windows / macOS)
- **Backend** — Self-hosted Supabase stack via Docker Compose, content volume, catalog indexer

See [AGENTS.md](AGENTS.md) for architecture rules, code style, and TDD workflow.

## Features

- Offline playback with local cache
- Auth with offline-safe JWT session (refresh only when online)
- Audiobook progress sync across devices (authenticated)
- Music — local playback only, no server progress sync
- Cycle auto-advance: track → next track → next book in cycle
- Desktop tray-first lifecycle (close/minimize → tray, quit via tray menu)
- Content volume → catalog indexer → available in apps

## Quick start

### Prerequisites

- Docker & Docker Compose
- Node.js 20+
- JDK 17+ (Android)
- Android SDK (Android builds)

### Backend

```bash
cp .env.example .env
# Edit secrets in .env

docker compose up -d --build
make test
```

Or simply:

```bash
docker compose up -d
```

(first run / after code changes: add `--build`)

Services:

| Service | URL / Port |
|---------|------------|
| Kong (API + Auth) | http://localhost:8000 |
| API | http://localhost:8000/api/v1 |
| Auth | http://localhost:8000/auth/v1 |
| Downloads (signed) | http://localhost:8080 |
| Postgres | :5432 |

Place audio files in the content volume — see [docs/content-layout.md](docs/content-layout.md).
Local default: `./data/content/` (override with `CONTENT_HOST_PATH` in `.env`).

### Desktop

One-time setup — use the **root** `.env` (same file as backend):

```bash
cp .env.example .env   # from repo root, edit TPLAYER_* URLs once
cd apps/desktop
npm install
npm run dev
```

The desktop app auto-loads `.env` from:

1. `apps/desktop/.env` (local overrides)
2. repo root `.env` (recommended)
3. `.env` next to the packaged executable (production builds)

**Android** — update `buildConfigField` in `apps/android/app/build.gradle.kts` for release.

## API

OpenAPI spec: [docs/openapi.yaml](docs/openapi.yaml)

## Deploy to VPS

Same as local — copy repo to server, configure `.env`, then:

```bash
docker compose up -d --build
```

Indexer runs automatically as a container (rescans content every `INDEXER_INTERVAL_SECONDS`).

## Project structure

```
tplayer/
├── AGENTS.md
├── apps/android/          # Kotlin Android app
├── apps/desktop/          # Electron desktop app
├── backend/
│   ├── api/               # REST API (catalog, signed URLs, progress)
│   ├── indexer/           # content volume scanner
│   └── supabase/migrations/
├── docker/                # nginx, kong, postgres init
├── docs/
├── scripts/
└── docker-compose.yml
```

## Testing

```bash
make test                 # indexer + api + desktop
scripts/smoke-test.sh     # includes Android unit tests
```

TDD is required for domain logic, indexer parsers, and API handlers. See AGENTS.md.

## Production deployment

1. Copy `.env.example` to `.env` and set strong secrets (`JWT_SECRET`, `POSTGRES_PASSWORD`, `DOWNLOAD_URL_SECRET`).
2. Set `API_EXTERNAL_URL`, `GOTRUE_SITE_URL`, `DOWNLOAD_BASE_URL` to your public domain.
3. Set `CONTENT_HOST_PATH` to your VPS content directory (e.g. `/var/tplayer/content`).
4. Run `docker compose up -d --build`.
5. Register a user via Supabase Auth (`POST /auth/v1/signup`) or disable signup and create users in GoTrue.
6. Configure client apps — desktop reads root `.env` automatically; Android uses `build.gradle.kts`.

### Client configuration

**Desktop** — set once in root `.env`:

- `TPLAYER_API_URL` — e.g. `https://your.domain/api/v1`
- `TPLAYER_SUPABASE_URL` — e.g. `https://your.domain`
- `TPLAYER_SUPABASE_ANON_KEY` — from `.env`

Then `cd apps/desktop && npm run dev` — no manual `set`/`export` needed.

### Security checklist

- [ ] Change all default secrets in `.env`
- [ ] Restrict Postgres port via firewall (only nginx/Kong public)
- [ ] Use HTTPS reverse proxy in front of Kong (Caddy/Traefik)
- [ ] Set `GOTRUE_DISABLE_SIGNUP=true` if you want invite-only registration

