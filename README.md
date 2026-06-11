# TPlayer

Offline-first cross-platform media player for audiobooks and music.

- **Android** — Kotlin, Jetpack Compose, Media3, Room
- **Desktop** — Electron, React (Windows / macOS)
- **Backend** — Self-hosted Supabase stack via Docker Compose, FTP content volume, catalog indexer

See [AGENTS.md](AGENTS.md) for architecture rules, code style, and TDD workflow.

## Features

- Offline playback with local cache
- Auth with offline-safe JWT session (refresh only when online)
- Audiobook progress sync across devices (authenticated)
- Music — local playback only, no server progress sync
- Cycle auto-advance: track → next track → next book in cycle
- Desktop tray-first lifecycle (close/minimize → tray, quit via tray menu)
- FTP upload → catalog indexer → available in apps

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

Services:

| Service | URL / Port |
|---------|------------|
| Kong (API + Auth) | http://localhost:8000 |
| API | http://localhost:8000/api/v1 |
| Auth | http://localhost:8000/auth/v1 |
| Downloads (signed) | http://localhost:8080 |
| FTP | :21 (passive 21100–21110) |
| Postgres | :5432 |

Upload content via FTP using [docs/content-layout.md](docs/content-layout.md).

### Desktop

```bash
cd apps/desktop
npm install
npm run dev      # development
npm test
npm run build
```

### Android

```bash
cd apps/android
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

Configure `API_BASE_URL` in `apps/android/app/build.gradle.kts` for your server.

## API

OpenAPI spec: [docs/openapi.yaml](docs/openapi.yaml)

## Deploy to VPS

```bash
chmod +x scripts/deploy-vps.sh scripts/smoke-test.sh
./scripts/deploy-vps.sh
```

## Project structure

```
tplayer/
├── AGENTS.md
├── apps/android/          # Kotlin Android app
├── apps/desktop/          # Electron desktop app
├── backend/
│   ├── api/               # REST API (catalog, signed URLs, progress)
│   ├── indexer/           # FTP volume scanner
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

1. Copy `.env.example` to `.env` and set strong secrets (`JWT_SECRET`, `POSTGRES_PASSWORD`, `DOWNLOAD_URL_SECRET`, `FTP_PASS`).
2. Set `API_EXTERNAL_URL`, `GOTRUE_SITE_URL`, `DOWNLOAD_BASE_URL` to your public domain.
3. Set `FTP_PASV_ADDRESS` to your VPS public IP.
4. Run `./scripts/deploy-vps.sh`.
5. Register a user via Supabase Auth (`POST /auth/v1/signup`) or disable signup and create users in GoTrue.
6. Configure client apps with your server URL and `SUPABASE_ANON_KEY`.

### Client configuration

**Desktop** — environment variables at build/run time:

- `TPLAYER_API_URL` — e.g. `https://your.domain/api/v1`
- `TPLAYER_SUPABASE_URL` — e.g. `https://your.domain`
- `TPLAYER_SUPABASE_ANON_KEY` — from `.env`

**Android** — update `build.gradle.kts` `buildConfigField` values or use product flavors for release.

### Security checklist

- [ ] Change all default secrets in `.env`
- [ ] Restrict FTP and Postgres ports via firewall (only nginx/Kong public)
- [ ] Use HTTPS reverse proxy in front of Kong (Caddy/Traefik)
- [ ] Set `GOTRUE_DISABLE_SIGNUP=true` if you want invite-only registration

