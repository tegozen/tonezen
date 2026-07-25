import type { EnqueueDownloadRequest } from "@core/downloads/downloadQueueState.js";
import type { Book, Track } from "@core/types.js";

interface NextAudiobookDownloadInput {
  book: Book;
  tracks: Track[];
  currentTrackId: string | null;
  savedTrackId: string | null;
}

export function nextAudiobookDownloadRequest({
  book,
  tracks,
  currentTrackId,
  savedTrackId,
}: NextAudiobookDownloadInput): EnqueueDownloadRequest | null {
  const track = nextAudiobookDownloadTrack(tracks, currentTrackId, savedTrackId);
  if (!track) return null;
  return {
    bookId: book.id,
    trackId: track.id,
    priority: "USER",
    title: track.title,
    subtitle: book.title,
    contentType: book.contentType,
  };
}

export function nextChapterInBook(tracks: Track[], currentTrackId: string): Track | null {
  const sorted = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  const index = sorted.findIndex((track) => track.id === currentTrackId);
  if (index < 0 || index >= sorted.length - 1) return null;
  return sorted[index + 1];
}

function nextAudiobookDownloadTrack(
  tracks: Track[],
  currentTrackId: string | null,
  savedTrackId: string | null,
): Track | null {
  const sorted = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  if (sorted.length === 0) return null;

  const currentIndex = currentTrackId
    ? sorted.findIndex((track) => track.id === currentTrackId)
    : -1;
  if (currentIndex >= 0) {
    return sorted.slice(currentIndex + 1).find((track) => !track.localPath) ?? null;
  }

  const savedIndex = savedTrackId ? sorted.findIndex((track) => track.id === savedTrackId) : -1;
  const startIndex = savedIndex >= 0 ? savedIndex : 0;
  return sorted.slice(startIndex).find((track) => !track.localPath) ?? null;
}
