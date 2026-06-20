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

export const MUSIC_QUEUE_INITIAL_WINDOW_SIZE = 24;
export const MUSIC_QUEUE_APPEND_WINDOW_SIZE = 12;
export const MUSIC_QUEUE_APPEND_TRIGGER_REMAINING = 4;

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

export function musicTrackArtist(track: Track, book: Book): string {
  return track.artist ?? book.author ?? book.title;
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
        artist: musicTrackArtist(track, book),
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
    .map((item): MusicListTrack | null => {
      const updated = freshById.get(item.trackId);
      if (!updated) return null;
      const next: MusicListTrack = { ...item, isDownloaded: updated.isDownloaded };
      if (updated.durationMs == null) {
        delete next.durationMs;
      } else {
        next.durationMs = updated.durationMs;
      }
      return next;
    })
    .filter((item): item is MusicListTrack => item != null);
}

function musicTrackMetadataMatches(left: MusicListTrack, right: MusicListTrack): boolean {
  return (
    left.trackTitle === right.trackTitle &&
    left.artist === right.artist &&
    left.albumTitle === right.albumTitle &&
    left.bookId === right.bookId &&
    left.durationMs === right.durationMs
  );
}

function musicListMetadataChanged(existing: MusicListTrack[], built: MusicListTrack[]): boolean {
  const freshById = new Map(built.map((track) => [track.trackId, track]));
  return existing.some((item) => {
    const fresh = freshById.get(item.trackId);
    return fresh != null && !musicTrackMetadataMatches(item, fresh);
  });
}

export function buildMusicTrackListForCatalogUpdate(
  existing: MusicListTrack[],
  books: Book[],
  tracks: Track[],
  musicStartedInSession: boolean,
  shuffleNewTracks: (tracks: MusicListTrack[]) => MusicListTrack[] = shuffleMusicTracks,
): MusicListTrack[] {
  const built = buildMusicTrackList(books, tracks);
  if (existing.length === 0) {
    return musicStartedInSession ? built : shuffleMusicTracks(built);
  }

  const existingIds = new Set(existing.map((track) => track.trackId));
  const builtIds = new Set(built.map((track) => track.trackId));
  const catalogChanged =
    built.length !== existing.length ||
    built.some((track) => !existingIds.has(track.trackId)) ||
    existing.some((track) => !builtIds.has(track.trackId)) ||
    musicListMetadataChanged(existing, built);

  if (!catalogChanged) {
    return refreshMusicTrackListDownloadState(existing, books, tracks);
  }

  const freshById = new Map(built.map((track) => [track.trackId, track]));
  const kept = existing
    .map((item) => freshById.get(item.trackId))
    .filter((item): item is MusicListTrack => item != null);
  const keptIds = new Set(kept.map((track) => track.trackId));
  const appended = built.filter((track) => !keptIds.has(track.trackId));
  return [...kept, ...shuffleNewTracks(appended)];
}

export function visibleMusicTrackList(
  tracks: MusicListTrack[],
  isNetworkOnline: boolean,
): MusicListTrack[] {
  return isNetworkOnline ? tracks : tracks.filter((track) => track.isDownloaded);
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

export function musicQueueWindowFrom(
  tracks: MusicListTrack[],
  startTrackId: string,
  size = MUSIC_QUEUE_INITIAL_WINDOW_SIZE,
): MusicListTrack[] {
  if (size <= 0) return [];
  return musicQueueFrom(tracks, startTrackId).slice(0, size);
}

export function nextMusicQueueWindow(
  tracks: MusicListTrack[],
  lastMaterializedTrackId: string,
  materializedTrackIds: Set<string>,
  size = MUSIC_QUEUE_APPEND_WINDOW_SIZE,
): MusicListTrack[] {
  if (tracks.length === 0 || size <= 0 || materializedTrackIds.size >= tracks.length) {
    return [];
  }
  const tailIndex = tracks.findIndex((track) => track.trackId === lastMaterializedTrackId);
  if (tailIndex < 0) return [];
  const result: MusicListTrack[] = [];
  let index = (tailIndex + 1) % tracks.length;
  for (let step = 0; step < tracks.length; step += 1) {
    const track = tracks[index];
    if (track && !materializedTrackIds.has(track.trackId)) {
      result.push(track);
      if (result.length === size) return result;
    }
    index = (index + 1) % tracks.length;
  }
  return result;
}

export function shouldAppendMusicQueueWindow(
  currentIndex: number,
  queueSize: number,
  remainingThreshold = MUSIC_QUEUE_APPEND_TRIGGER_REMAINING,
): boolean {
  if (queueSize <= 0 || currentIndex < 0 || currentIndex >= queueSize) return false;
  return queueSize - currentIndex - 1 <= remainingThreshold;
}

export function nextMusicIndex(currentIndex: number, size: number): number {
  if (size <= 0) return -1;
  return (currentIndex + 1) % size;
}

export function previousMusicIndex(currentIndex: number, size: number): number {
  if (size <= 0) return -1;
  return (currentIndex - 1 + size) % size;
}
