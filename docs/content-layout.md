# Content Volume Layout

Place audio files on the content volume using this directory structure.
Local dev: `./data/content/`. Production: set `CONTENT_HOST_PATH` in `.env` to your host path.
The catalog indexer scans these paths and upserts metadata into Postgres.

## Root structure

```
/content/
  cycles/          # audiobooks grouped in cycles
  music/           # music albums (no cycles)
```

## Audiobooks (cycles)

```
/content/cycles/{cycle-slug}/
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
/content/music/{album-slug}/
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

- Computes SHA-256 checksum and file size for each audio file
- Extracts duration via ffprobe when available
- Sets `deleted_at` on catalog entries removed from the volume (soft delete)
- Rescan interval configurable via `INDEXER_INTERVAL_SECONDS`

## Volume mount

Indexer and nginx read the same host directory, mounted at `/content` inside containers.
Upload content however you manage the VPS (FTP, rsync, scp, etc.) — TPlayer only needs the files on disk.
