import type { AudiobookProgress, Book, Cycle, Track } from "./types.js";

export function resolveBookListenedMs(
  tracks: Track[],
  progress: AudiobookProgress | null | undefined,
): number {
  if (!progress) return 0;
  const sorted = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  const progressIndex = sorted.findIndex(
    (track) => track.id === progress.trackId && track.bookId === progress.bookId,
  );
  if (progressIndex < 0) return 0;

  const listenedBefore = sorted
    .slice(0, progressIndex)
    .reduce((sum, track) => sum + (track.durationMs ?? 0), 0);
  const currentDuration = sorted[progressIndex].durationMs ?? 0;
  const positionMs =
    currentDuration > 0 ? Math.min(progress.positionMs, currentDuration) : progress.positionMs;
  return listenedBefore + positionMs;
}

export function orderedCycleBooks(cycle: Cycle): Book[] {
  const ordered = cycle.bookOrder
    .map((slug) => cycle.books.find((book) => book.slug === slug))
    .filter((book): book is Book => book != null);
  return ordered.length > 0 ? ordered : cycle.books;
}

export function cycleBookIds(cycle: Cycle): Set<string> {
  return new Set(orderedCycleBooks(cycle).map((book) => book.id).filter(Boolean));
}
