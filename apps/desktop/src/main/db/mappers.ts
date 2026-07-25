import type { Book, Track } from "@core/types.js";
import { parseWaveformPeaksJson } from "@core/catalog/waveformPeaks.js";

export interface BookRow {
  id: string;
  slug: string;
  content_type: string;
  title: string;
  author: string | null;
}

export interface TrackRow {
  id: string;
  book_id: string;
  sort_order: number;
  title: string;
  filename: string;
  artist: string | null;
  duration_ms: number | null;
  local_path: string | null;
  local_downloaded_at: number | null;
  waveform_peaks_json: string | null;
}

export function mapBookRow(row: BookRow): Book {
  return {
    id: row.id,
    slug: row.slug,
    contentType: row.content_type as Book["contentType"],
    title: row.title,
    author: row.author ?? undefined,
  };
}

export function mapTrackRow(row: TrackRow): Track {
  return {
    id: row.id,
    bookId: row.book_id,
    sortOrder: row.sort_order,
    title: row.title,
    filename: row.filename,
    artist: row.artist ?? undefined,
    durationMs: row.duration_ms ?? undefined,
    localPath: row.local_path ?? undefined,
    localDownloadedAt: row.local_downloaded_at ?? undefined,
    waveformPeaks: parseWaveformPeaksJson(row.waveform_peaks_json) ?? undefined,
  };
}
