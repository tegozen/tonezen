# Content Storage Layout

Upload audio to **Supabase Storage** bucket `content` via Studio (http://localhost:8000 → Storage). On `docker compose up`, `storage-bootstrap` ensures **`cycles/`** and **`music/`** exist (via empty `.gitkeep` placeholders).

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

- Scans `content/tonezen/content/` inside the `tonezen-storage` Docker volume (Supabase Storage file backend)
- Computes SHA-256 checksum and file size for each audio file
- Extracts duration and ID3/metadata tags via ffprobe when available
- Sets `deleted_at` on catalog entries removed from storage (soft delete)
- Rescan interval: 60 seconds (hardcoded in `docker-compose.yml`)

## Upload workflow

1. Open Studio → **Storage** → bucket **content** → open **`cycles`** or **`music`**
2. Upload audiobook folders under **`cycles`**, or drop music files into **`music`**
3. Indexer picks up new files on the next scan
4. Clients download via Storage signed URLs from `POST /api/v1/downloads/sign`

### Large files (> ~6 MB)

Studio switches to **TUS** (resumable upload) for bigger files. Requires `storage-api` **v1.29+** with `REQUEST_ALLOW_X_FORWARDED_PATH=true` and `STORAGE_PUBLIC_URL` set (see `docker-compose.yml`).

If only small files succeed and larger ones fail with `Invalid URL`:

1. Hard-refresh Studio (`Ctrl+F5`)
2. Retry failed files only (TUS may cache broken upload URLs in the browser)
3. If needed, clear site data for `http://localhost:8000`
4. After recreating the storage container, run `docker compose restart kong` (Kong may cache a stale upstream)

Alternative: copy files into the `tonezen-storage` Docker volume or use `make storage-import`.
