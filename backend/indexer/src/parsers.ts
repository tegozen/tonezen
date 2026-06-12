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

export interface MusicFileScan {
  filename: string;
  title: string | null;
  artist: string | null;
  album: string | null;
  trackNumber: number | null;
}

const AUDIO_EXTENSIONS = new Set([".mp3", ".flac", ".m4a", ".ogg", ".opus", ".wav", ".aac"]);

export function isAudioFilename(filename: string): boolean {
  const dot = filename.lastIndexOf(".");
  if (dot < 0) return false;
  return AUDIO_EXTENSIONS.has(filename.slice(dot).toLowerCase());
}

export function slugify(text: string): string {
  return (
    text
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "") || "untitled"
  );
}

export function filenameBase(filename: string): string {
  const dot = filename.lastIndexOf(".");
  return dot >= 0 ? filename.slice(0, dot) : filename;
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

export function buildMusicAlbums(files: MusicFileScan[]): ParsedBook[] {
  const groups = new Map<
    string,
    { albumTitle: string; artist: string | null; tracks: ParsedTrack[] }
  >();

  for (const file of files) {
    const trackTitle = file.title?.trim() || trackTitleFromFilename(file.filename);
    const artist = file.artist?.trim() || null;
    const album = file.album?.trim() || null;

    let groupKey: string;
    let albumTitle: string;

    if (!album) {
      groupKey = slugify(filenameBase(file.filename));
      albumTitle = trackTitle;
    } else {
      groupKey = slugify(`${artist ?? "unknown"}-${album}`);
      albumTitle = album;
    }

    if (!groups.has(groupKey)) {
      groups.set(groupKey, { albumTitle, artist, tracks: [] });
    }

    const group = groups.get(groupKey)!;
    if (!group.artist && artist) {
      group.artist = artist;
    }

    group.tracks.push({
      filename: file.filename,
      sortOrder: file.trackNumber != null ? file.trackNumber - 1 : group.tracks.length,
      title: trackTitle,
    });
  }

  const albums: ParsedBook[] = [];
  for (const [slug, group] of groups) {
    group.tracks.sort((a, b) => {
      if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
      return a.filename.localeCompare(b.filename);
    });
    group.tracks.forEach((track, index) => {
      track.sortOrder = index;
    });

    albums.push({
      slug,
      contentType: "music",
      title: group.albumTitle,
      author: group.artist,
      coverPath: null,
      tracks: group.tracks,
    });
  }

  return albums;
}

export function storagePathForAudiobook(
  cycleSlug: string,
  bookSlug: string,
  filename: string,
): string {
  return `cycles/${cycleSlug}/books/${bookSlug}/audio/${filename}`;
}

export function storagePathForMusic(filename: string): string {
  return `music/${filename}`;
}
