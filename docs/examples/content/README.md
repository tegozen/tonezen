# Example content layout

Reference tree for local testing. Upload real audio to **Studio → Storage → bucket `content`**
using the layout in [content-layout.md](../content-layout.md).

No JSON metadata is required. The indexer derives titles from directory names and orders
books/tracks by sorted directory and file names. Author and track titles come from audio
tags when present.

These paths are not scanned automatically — they document the expected on-disk shape only.

```
cycles/sample-cycle/sample-book/001-intro.mp3
music/01-track.mp3
```
