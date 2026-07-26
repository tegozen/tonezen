import type { Track } from "@core/types.js";
import { serializeWaveformPeaks } from "@core/catalog/waveformPeaks.js";
import { getDb } from "../db/connection.js";
import { mapTrackRow, type TrackRow } from "../db/mappers.js";

export function upsertTracks(tracks: Track[]): void {
  const stmt = getDb().prepare(`
    INSERT INTO tracks (id, book_id, sort_order, title, filename, artist, duration_ms, local_path, waveform_peaks_json)
    VALUES (@id, @bookId, @sortOrder, @title, @filename, @artist, @durationMs, @localPath, @waveformPeaksJson)
    ON CONFLICT(id) DO UPDATE SET
      book_id = excluded.book_id,
      sort_order = excluded.sort_order,
      title = excluded.title,
      filename = excluded.filename,
      artist = excluded.artist,
      duration_ms = excluded.duration_ms,
      local_path = COALESCE(excluded.local_path, tracks.local_path),
      waveform_peaks_json = excluded.waveform_peaks_json
  `);
  const tx = getDb().transaction((items: Track[]) => {
    for (const track of items) {
      stmt.run({
        ...track,
        artist: track.artist ?? null,
        durationMs: track.durationMs ?? null,
        localPath: track.localPath ?? null,
        waveformPeaksJson: serializeWaveformPeaks(track.waveformPeaks),
      });
    }
  });
  tx(tracks);
}

/** Drop tracks whose book is no longer in the remote catalog. */
export function deleteTracksForBooksNotIn(bookIds: string[]): void {
  if (bookIds.length === 0) {
    getDb().prepare(`DELETE FROM tracks`).run();
    return;
  }
  const placeholders = bookIds.map(() => "?").join(",");
  getDb()
    .prepare(`DELETE FROM tracks WHERE book_id NOT IN (${placeholders})`)
    .run(...bookIds);
}

/** Drop tracks removed from a book during sync (keeps local_path rows only for remote ids). */
export function deleteTracksNotIn(bookId: string, trackIds: string[]): void {
  if (trackIds.length === 0) {
    getDb().prepare(`DELETE FROM tracks WHERE book_id = ?`).run(bookId);
    return;
  }
  const placeholders = trackIds.map(() => "?").join(",");
  getDb()
    .prepare(`DELETE FROM tracks WHERE book_id = ? AND id NOT IN (${placeholders})`)
    .run(bookId, ...trackIds);
}

export function getTrackById(trackId: string): Track | null {
  const row = getDb()
    .prepare(
      `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
       FROM tracks WHERE id = ? LIMIT 1`,
    )
    .get(trackId) as TrackRow | undefined;
  return row ? mapTrackRow(row) : null;
}

export function getAllTracks(): Track[] {
  const rows = getDb()
    .prepare(
      `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
       FROM tracks ORDER BY book_id, sort_order`,
    )
    .all() as TrackRow[];
  return rows.map(mapTrackRow);
}

export function getTracks(bookId: string): Track[] {
  const rows = getDb()
    .prepare(
      `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
       FROM tracks WHERE book_id = ? ORDER BY sort_order`,
    )
    .all(bookId) as TrackRow[];
  return rows.map(mapTrackRow);
}
