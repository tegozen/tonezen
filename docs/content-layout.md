# Content Storage Layout

Upload audio to **Supabase Storage** bucket `content` via Studio (http://localhost:8000 → Storage).

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

```
content/music/{album-slug}/
  album.json
  cover.jpg            # optional
  audio/
    01-track.mp3
    02-track.mp3
```

### album.json

```json
{
  "content_type": "music",
  "title": "Album Name",
  "author": "Artist Name",
  "track_order": ["01-track.mp3", "02-track.mp3"]
}
```

## Indexer behavior

- Scans `tonezen/content/` inside the `tonezen-storage` Docker volume
- Computes SHA-256 checksum and file size for each audio file
- Extracts duration via ffprobe when available
- Sets `deleted_at` on catalog entries removed from storage (soft delete)
- Rescan interval: 60 seconds (hardcoded in `docker-compose.yml`)

## Upload workflow

1. Open Studio → **Storage** → bucket **content**
2. Upload folders/files following the layout above (or drag-and-drop a prepared tree)
3. Indexer picks up new files on the next scan
4. Clients download via Storage signed URLs from `POST /api/v1/downloads/sign`
