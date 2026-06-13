import type { Book, Track } from "./types.js";

export interface MusicListTrack {
  trackId: string;
  trackTitle: string;
  artist: string;
  albumTitle: string;
  bookId: string;
  durationMs?: number;
  isDownloaded: boolean;
}

export function buildMusicTrackList(books: Book[], tracks: Track[]): MusicListTrack[] {
  const musicBooks = books.filter((b) => b.contentType === "music");
  const bookById = new Map(musicBooks.map((b) => [b.id, b]));
  return tracks
    .filter((t) => bookById.has(t.bookId))
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
