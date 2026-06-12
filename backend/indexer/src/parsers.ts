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

export function titleFromSlug(slug: string): string {
  return slug.replace(/-/g, " ").trim() || slug;
}

export function trackTitleFromFilename(filename: string): string {
  const base = filename.replace(/\.[^.]+$/, "");
  return base.replace(/^\d+-/, "").replace(/-/g, " ").trim() || filename;
}

export interface AudiobookFileScan {
  filename: string;
  title: string | null;
  artist: string | null;
}

export function buildTracks(trackOrder: string[]): ParsedTrack[] {
  return buildAudiobookTracks(trackOrder.map((filename) => ({ filename, title: null, artist: null })));
}

export function buildAudiobookTracks(files: AudiobookFileScan[]): ParsedTrack[] {
  return files.map((file, index) => ({
    filename: file.filename,
    sortOrder: index,
    title: file.title?.trim() || trackTitleFromFilename(file.filename),
  }));
}

export function pickAudiobookAuthor(files: AudiobookFileScan[]): string | null {
  return files.map((file) => file.artist?.trim() || null).find(Boolean) ?? null;
}

export function buildMusicLibrary(files: MusicFileScan[]): ParsedBook[] {
  if (files.length === 0) return [];

  const tracks = files.map((file, index) => ({
    filename: file.filename,
    sortOrder: file.trackNumber != null ? file.trackNumber - 1 : index,
    title: file.title?.trim() || trackTitleFromFilename(file.filename),
  }));

  tracks.sort((a, b) => {
    if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
    return a.filename.localeCompare(b.filename);
  });
  tracks.forEach((track, index) => {
    track.sortOrder = index;
  });

  const artist = files.map((file) => file.artist?.trim() || null).find(Boolean) ?? null;

  return [
    {
      slug: "music-library",
      contentType: "music",
      title: "Music",
      author: artist,
      coverPath: null,
      tracks,
    },
  ];
}

export function storagePathForAudiobook(
  cycleSlug: string,
  bookSlug: string,
  filename: string,
): string {
  return `cycles/${cycleSlug}/${bookSlug}/${filename}`;
}

export function storagePathForMusic(filename: string): string {
  return `music/${filename}`;
}
