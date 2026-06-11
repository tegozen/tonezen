export interface CycleMeta {
  title: string;
  description?: string;
  book_order: string[];
}

export interface BookMeta {
  content_type: "audiobook" | "music";
  title: string;
  author?: string;
  track_order: string[];
}

export interface ParsedTrack {
  filename: string;
  sortOrder: number;
  title: string;
}

export interface ParsedBook {
  slug: string;
  contentType: "audiobook" | "music";
  title: string;
  author: string | null;
  coverPath: string | null;
  tracks: ParsedTrack[];
}

export interface ParsedCycle {
  slug: string;
  title: string;
  description: string | null;
  bookOrder: string[];
  books: ParsedBook[];
}

export function parseCycleMeta(raw: unknown): CycleMeta {
  if (!raw || typeof raw !== "object") {
    throw new Error("Invalid cycle.json: expected object");
  }
  const obj = raw as Record<string, unknown>;
  if (typeof obj.title !== "string" || !obj.title.trim()) {
    throw new Error("Invalid cycle.json: title required");
  }
  if (!Array.isArray(obj.book_order)) {
    throw new Error("Invalid cycle.json: book_order must be array");
  }
  return {
    title: obj.title.trim(),
    description: typeof obj.description === "string" ? obj.description : undefined,
    book_order: obj.book_order.map(String),
  };
}

export function parseBookMeta(raw: unknown, defaultType: "audiobook" | "music"): BookMeta {
  if (!raw || typeof raw !== "object") {
    throw new Error("Invalid book.json: expected object");
  }
  const obj = raw as Record<string, unknown>;
  if (typeof obj.title !== "string" || !obj.title.trim()) {
    throw new Error("Invalid book.json: title required");
  }
  if (!Array.isArray(obj.track_order)) {
    throw new Error("Invalid book.json: track_order must be array");
  }
  const contentType =
    obj.content_type === "music" || obj.content_type === "audiobook"
      ? obj.content_type
      : defaultType;
  return {
    content_type: contentType,
    title: obj.title.trim(),
    author: typeof obj.author === "string" ? obj.author : undefined,
    track_order: obj.track_order.map(String),
  };
}

export function trackTitleFromFilename(filename: string): string {
  const base = filename.replace(/\.[^.]+$/, "");
  return base.replace(/^\d+-/, "").replace(/-/g, " ").trim() || filename;
}

export function buildTracks(trackOrder: string[]): ParsedTrack[] {
  return trackOrder.map((filename, index) => ({
    filename,
    sortOrder: index,
    title: trackTitleFromFilename(filename),
  }));
}

export function storagePathForAudiobook(
  cycleSlug: string,
  bookSlug: string,
  filename: string,
): string {
  return `cycles/${cycleSlug}/books/${bookSlug}/audio/${filename}`;
}

export function storagePathForMusic(albumSlug: string, filename: string): string {
  return `music/${albumSlug}/audio/${filename}`;
}
