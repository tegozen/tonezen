# Content Storage Layout

Upload audio to **Supabase Storage** bucket `content` via Studio (http://localhost:8000 → Storage).

On `docker compose up`, the `storage-bootstrap` service creates **`cycles/`** and **`music/`** inside the bucket (with `README.txt` hints). Upload only into those folders.

Use this folder structure inside the bucket. The catalog indexer scans the storage file backend and upserts metadata into Postgres.

## Root structure

```
content/
  cycles/          # audiobooks grouped in cycles
  music/           # music albums (no cycles)
```

## Audiobooks (cycles)

```
content/cycles/{cycle-slug}/
  cycle.json
  books/
    {book-slug}/
      book.json
      cover.jpg          # optional
      audio/
        001-intro.mp3
        002-chapter-01.mp3
```

### cycle.json

```json
{
  "title": "Horus Heresy",
  "description": "Optional description",
  "book_order": ["fallen-angels", "angels-demons"]
}
```

### book.json

```json
{
  "content_type": "audiobook",
  "title": "Fallen Angels",
  "author": "Mike Lee",
  "track_order": ["001-intro.mp3", "002-chapter-01.mp3"]
}
```

## Music

Drop audio files directly into `music/` — no folders or JSON metadata required.

```
content/music/
  01-track.mp3
  02-track.mp3
  artist-song.flac
```

The indexer reads tags from each file (title, artist, album, track number). Missing tags fall back to the filename. Tracks with the same album tag are grouped into one album; files without an album tag become standalone singles.

## Indexer behavior

- Scans `tonezen/content/` inside the `tonezen-storage` Docker volume
- Computes SHA-256 checksum and file size for each audio file
- Extracts duration and ID3/metadata tags via ffprobe when available
- Sets `deleted_at` on catalog entries removed from storage (soft delete)
- Rescan interval: 60 seconds (hardcoded in `docker-compose.yml`)

## Upload workflow

1. Open Studio → **Storage** → bucket **content** → open **`cycles`** or **`music`**
2. Upload audiobook folders under **`cycles`**, or drop music files into **`music`**
3. Indexer picks up new files on the next scan
4. Clients download via Storage signed URLs from `POST /api/v1/downloads/sign`
