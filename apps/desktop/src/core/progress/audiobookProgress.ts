import type { AudiobookProgress, Book, Track } from "@core/types.js";

export function completedAudiobookProgress(
  book: Book | null | undefined,
  track: Track | null | undefined,
  fallbackDurationMs: number,
  updatedAt: string = new Date().toISOString(),
): AudiobookProgress | null {
  if (!book || book.contentType !== "audiobook" || !track || track.bookId !== book.id) {
    return null;
  }
  const positionMs = Math.max(track.durationMs ?? 0, fallbackDurationMs, 0);
  if (positionMs <= 0) return null;
  return {
    bookId: book.id,
    trackId: track.id,
    positionMs,
    updatedAt,
  };
}

export function upsertAudiobookProgress(
  progressList: AudiobookProgress[],
  progress: AudiobookProgress,
): AudiobookProgress[] {
  const next = progressList.filter((item) => item.bookId !== progress.bookId);
  next.push(progress);
  return next;
}
