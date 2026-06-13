import type { Book, Track } from "../../shared/types.js";

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
  duration_ms: number | null;
  local_path: string | null;
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
    durationMs: row.duration_ms ?? undefined,
    localPath: row.local_path ?? undefined,
  };
}
