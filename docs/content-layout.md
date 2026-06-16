# Content Storage Layout

Upload audio to **Supabase Storage** bucket `content` via Studio (http://localhost:8000/studio → Storage). On `docker compose up`, the **`seed`** service ensures the default admin user and **`cycles/`** / **`music/`** layout exist (via empty `.gitkeep` placeholders). Re-run manually: `make seed`.

Files are stored in **Beget S3** (shared bucket for dev and prod). Local dev uses the same bucket as production — uploads and deletes in Studio affect production files.

## Root structure

```
content/
  cycles/          # audiobooks grouped in cycles
  music/           # flat music library (no cycles)
```

## Audiobooks (cycles)

No JSON metadata. Names and playback order come from directory and file names.

```
content/cycles/{cycle-slug}/
  {book-slug}/
    001-intro.mp3
    002-chapter-01.mp3
```

| What                | Source                                                      |
| ------------------- | ----------------------------------------------------------- |
| Cycle title         | `{cycle-slug}` directory name (hyphens → spaces)            |
| Book order in cycle | `{book-slug}` subdirectories sorted by name                 |
| Book title          | `{book-slug}` directory name (hyphens → spaces)             |
| Track order in book | audio files in the book folder sorted by filename           |
| Author              | ID3/metadata tags (`artist`, `album_artist`); UI placeholder if missing |
| Cover               | embedded artwork in audio tags; UI gradient placeholder if missing |

Example: `cycles/horus-heresy/fallen-angels/001-intro.mp3` → cycle «horus heresy», book «fallen angels», first track «intro» (or tag title when present).

## Music

Drop audio files directly into `music/` — no folders or JSON metadata required.

```
content/music/
  01-track.mp3
  02-track.mp3
  artist-song.flac
```

The indexer reads tags from each file (title, artist, track number). All files become one flat music library; album tags are ignored.

## Indexer behavior

- Lists objects from `storage.objects` (bucket `content`, prefixes `cycles/` and `music/`)
- Downloads new/changed files via Storage API for SHA-256 checksum and ffprobe metadata
- Sets `deleted_at` on catalog entries removed from storage (soft delete)
- Rescan interval: 60 seconds (hardcoded in `docker-compose.yml`)

## Upload workflow

1. Open Studio → **Storage** → bucket **content** → open **`cycles`** or **`music`**
2. Upload audiobook folders under **`cycles`**, or drop music files into **`music`**
3. Indexer picks up new files on the next scan
4. Clients download via Storage signed URLs from `POST /api/v1/downloads/sign`

### Large files (> ~6 MB)

Studio switches to **TUS** (resumable upload) for bigger files. Requires `storage-api` **v1.60+** with `REQUEST_ALLOW_X_FORWARDED_PATH=true` and `STORAGE_PUBLIC_URL` set to the public HTTPS URL (see `docker-compose.yml` and `TONEZEN_BASE_URL` in `.env`).

If Studio shows **«Failed to upload 1 file!»**:

1. **`TONEZEN_BASE_URL` must match how you open Studio** — local dev: `http://localhost:8000`; prod: `https://your-domain` (no `:8000`). If local Studio (`localhost:8000`) points at prod URL, browser CORS blocks uploads.
2. **Large files (> ~6 MB)** — TUS `Location` must be HTTPS without internal port. After changing `TONEZEN_BASE_URL`, run `docker compose up -d storage kong`.
3. Hard-refresh Studio (`Ctrl+F5`) and retry failed files only (TUS may cache broken upload URLs).

If only small files succeed and larger ones fail with `Invalid URL`:

1. Hard-refresh Studio (`Ctrl+F5`)
2. Retry failed files only (TUS may cache broken upload URLs in the browser)
3. If needed, clear site data for `http://localhost:8000`
4. After recreating the storage container, run `docker compose restart kong` (Kong may cache a stale upstream)

If large uploads fail silently (Studio shows progress but files never appear), check `docker compose logs storage` for `XAmzContentSHA256Mismatch`. Beget S3 requires `AWS_REQUEST_CHECKSUM_CALCULATION=WHEN_REQUIRED` on the storage service (see `docker-compose.yml`). Delete orphaned objects in the Beget panel under `tonezen/content/` and re-upload via Studio.

## S3 credentials

Set in root `.env` (from Beget panel → Object storage → Access keys):

- `S3_BUCKET`, `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`

Dev and prod share the same bucket. Postgres is local per environment; catalog is rebuilt by indexer on each stack.

## Backup

Use Beget panel or S3 tools (`aws s3 sync`, rclone) against the Beget bucket — not Docker volume export.
