import type { Book, Track } from "./types.js";

export const MUSIC_LIBRARY_SLUG = "music-library";

export interface MusicListTrack {
  trackId: string;
  trackTitle: string;
  artist: string;
  albumTitle: string;
  bookId: string;
  durationMs?: number;
  isDownloaded: boolean;
}

export function resolveMusicLibraryBooks(books: Book[]): Book[] {
  const musicBooks = books.filter((book) => book.contentType === "music");
  const libraryBooks = musicBooks.filter((book) => book.slug === MUSIC_LIBRARY_SLUG);
  return libraryBooks.length > 0 ? libraryBooks : musicBooks;
}

export function resolveMusicLibraryTracks(books: Book[], tracks: Track[]): Track[] {
  const sourceBooks = resolveMusicLibraryBooks(books);
  const bookIds = new Set(sourceBooks.map((book) => book.id));
  const seen = new Set<string>();
  return tracks
    .filter((track) => bookIds.has(track.bookId))
    .sort((left, right) => {
      const sortOrder = left.sortOrder - right.sortOrder;
      if (sortOrder !== 0) return sortOrder;
      const filename = left.filename.localeCompare(right.filename, undefined, { sensitivity: "base" });
      if (filename !== 0) return filename;
      const title = left.title.localeCompare(right.title, undefined, { sensitivity: "base" });
      if (title !== 0) return title;
      return left.id.localeCompare(right.id);
    })
    .filter((track) => {
      if (seen.has(track.id)) return false;
      seen.add(track.id);
      return true;
    });
}

export function buildMusicTrackList(books: Book[], tracks: Track[]): MusicListTrack[] {
  const bookById = new Map(resolveMusicLibraryBooks(books).map((book) => [book.id, book]));
  return resolveMusicLibraryTracks(books, tracks)
    .filter((track) => bookById.has(track.bookId))
    .map((track) => {
      const book = bookById.get(track.bookId)!;
      return {
        trackId: track.id,
        trackTitle: track.title,
        artist: book.author ?? book.title,
        albumTitle: book.title,
        bookId: track.bookId,
        durationMs: track.durationMs,
        isDownloaded: Boolean(track.localPath),
      };
    });
}

export function shuffleMusicTracks(tracks: MusicListTrack[]): MusicListTrack[] {
  const copy = [...tracks];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

export function refreshMusicTrackListDownloadState(
  list: MusicListTrack[],
  books: Book[],
  tracks: Track[],
): MusicListTrack[] {
  const freshById = new Map(
    buildMusicTrackList(books, tracks).map((track) => [track.trackId, track]),
  );
  return list
    .map((item) => {
      const updated = freshById.get(item.trackId);
      if (!updated) return null;
      return { ...item, isDownloaded: updated.isDownloaded, durationMs: updated.durationMs };
    })
    .filter((item): item is MusicListTrack => item != null);
}

export function buildMusicTrackListForCatalogUpdate(
  existing: MusicListTrack[],
  books: Book[],
  tracks: Track[],
  musicStartedInSession: boolean,
): MusicListTrack[] {
  if (existing.length > 0) {
    return refreshMusicTrackListDownloadState(existing, books, tracks);
  }
  const built = buildMusicTrackList(books, tracks);
  return musicStartedInSession ? built : shuffleMusicTracks(built);
}

export function musicQueueFrom(
  tracks: MusicListTrack[],
  startTrackId: string,
): MusicListTrack[] {
  if (tracks.length <= 1) return tracks;
  const index = tracks.findIndex((t) => t.trackId === startTrackId);
  if (index < 0) return tracks;
  return [...tracks.slice(index), ...tracks.slice(0, index)];
}

export function nextMusicIndex(currentIndex: number, size: number): number {
  if (size <= 0) return -1;
  return (currentIndex + 1) % size;
}

export function previousMusicIndex(currentIndex: number, size: number): number {
  if (size <= 0) return -1;
  return (currentIndex - 1 + size) % size;
}
