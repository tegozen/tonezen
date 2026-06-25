import type { AudiobookProgress, Book, Cycle, Track } from "./types.js";

const COMPLETED_FRACTION_THRESHOLD = 0.95;

export interface CycleResumeTarget {
  book: Book;
  track: Track;
  startPositionMs: number;
}

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

export function resolveCycleListenFraction(
  cycle: Cycle,
  tracksByBookId: Map<string, Track[]>,
  progressByBookId: Map<string, AudiobookProgress | null | undefined>,
): number | null {
  let totalMs = 0;
  let listenedMs = 0;
  for (const book of orderedCycleBooks(cycle)) {
    const tracks = tracksByBookId.get(book.id) ?? [];
    const bookTotalMs = tracks.reduce((sum, track) => sum + (track.durationMs ?? 0), 0);
    if (bookTotalMs <= 0) continue;
    totalMs += bookTotalMs;
    listenedMs += resolveBookListenedMs(tracks, progressByBookId.get(book.id));
  }
  if (totalMs <= 0) return null;
  return Math.min(1, Math.max(0, listenedMs / totalMs));
}

export function isCycleFullyListened(
  cycle: Cycle,
  tracksByBookId: Map<string, Track[]>,
  progressByBookId: Map<string, AudiobookProgress | null | undefined>,
): boolean {
  return (resolveCycleListenFraction(cycle, tracksByBookId, progressByBookId) ?? 0) >=
    COMPLETED_FRACTION_THRESHOLD;
}

export function resolveCycleResumeTarget(
  cycle: Cycle,
  tracksByBookId: Map<string, Track[]>,
  progressByBookId: Map<string, AudiobookProgress | null | undefined>,
): CycleResumeTarget | null {
  let lastBookWithProgress: Book | null = null;
  let lastProgress: AudiobookProgress | null = null;
  for (const book of orderedCycleBooks(cycle)) {
    const progress = progressByBookId.get(book.id);
    if (progress) {
      lastBookWithProgress = book;
      lastProgress = progress;
    }
  }
  if (lastBookWithProgress && lastProgress) {
    return resolveBookResumeTarget(cycle, lastBookWithProgress, tracksByBookId, lastProgress);
  }
  const firstBook = orderedCycleBooks(cycle)[0];
  if (!firstBook) return null;
  const firstTracks = [...(tracksByBookId.get(firstBook.id) ?? [])].sort(
    (a, b) => a.sortOrder - b.sortOrder,
  );
  const firstTrack = firstTracks[0];
  if (!firstTrack) return null;
  return { book: firstBook, track: firstTrack, startPositionMs: 0 };
}

function resolveBookResumeTarget(
  cycle: Cycle,
  book: Book,
  tracksByBookId: Map<string, Track[]>,
  progress: AudiobookProgress,
): CycleResumeTarget | null {
  const tracks = [...(tracksByBookId.get(book.id) ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
  const firstTrack = tracks[0];
  if (!firstTrack) return null;
  const trackIndex = tracks.findIndex((track) => track.id === progress.trackId);
  if (trackIndex < 0) {
    return { book, track: firstTrack, startPositionMs: 0 };
  }
  const track = tracks[trackIndex];
  const durationMs = track.durationMs ?? 0;
  const isTrackComplete =
    durationMs > 0 && progress.positionMs >= durationMs * COMPLETED_FRACTION_THRESHOLD;
  if (!isTrackComplete) {
    return { book, track, startPositionMs: progress.positionMs };
  }
  if (trackIndex < tracks.length - 1) {
    return { book, track: tracks[trackIndex + 1], startPositionMs: 0 };
  }
  const bookIndex = cycle.bookOrder.indexOf(book.slug);
  if (bookIndex >= 0 && bookIndex < cycle.bookOrder.length - 1) {
    const nextBook = cycle.books.find((item) => item.slug === cycle.bookOrder[bookIndex + 1]);
    if (nextBook) {
      const nextTracks = [...(tracksByBookId.get(nextBook.id) ?? [])].sort(
        (a, b) => a.sortOrder - b.sortOrder,
      );
      const nextTrack = nextTracks[0];
      if (nextTrack) {
        return { book: nextBook, track: nextTrack, startPositionMs: 0 };
      }
    }
  }
  const restartBook = orderedCycleBooks(cycle)[0];
  if (!restartBook) return null;
  const restartTracks = [...(tracksByBookId.get(restartBook.id) ?? [])].sort(
    (a, b) => a.sortOrder - b.sortOrder,
  );
  const restartTrack = restartTracks[0];
  if (!restartTrack) return null;
  return { book: restartBook, track: restartTrack, startPositionMs: 0 };
}

export function orderedCycleEntriesFromResume(
  cycle: Cycle,
  tracksByBookId: Map<string, Track[]>,
  resume: CycleResumeTarget,
): Array<{ book: Book; track: Track }> {
  const entries: Array<{ book: Book; track: Track }> = [];
  let reachedResume = false;
  for (const book of orderedCycleBooks(cycle)) {
    const tracks = [...(tracksByBookId.get(book.id) ?? [])].sort(
      (a, b) => a.sortOrder - b.sortOrder,
    );
    for (const track of tracks) {
      if (!reachedResume) {
        if (book.id === resume.book.id && track.id === resume.track.id) {
          reachedResume = true;
          entries.push({ book, track });
        }
      } else {
        entries.push({ book, track });
      }
    }
  }
  return entries;
}
