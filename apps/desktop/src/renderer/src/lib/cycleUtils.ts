import type { AudiobookProgress, Book, Cycle, Track } from "@shared/types";
import { cycleBookIds, resolveBookListenedMs } from "@shared/cycleListenProgress";
import type { LibraryFilter } from "../i18n/strings";
import { canContinueBookListening, type BookContinueState } from "./bookTrackUtils";

export interface CycleCardState {
  isDownloaded: boolean;
  progressFraction: number | null;
  continueState: BookContinueState | null;
  showDownload: boolean;
  showRemoveDownload: boolean;
  isListened: boolean;
}

export function buildTracksByBookId(allTracks: Track[]): Map<string, Track[]> {
  const map = new Map<string, Track[]>();
  for (const track of allTracks) {
    const list = map.get(track.bookId);
    if (list) list.push(track);
    else map.set(track.bookId, [track]);
  }
  return map;
}

export function resolveCycleContinueState(
  cycle: Cycle,
  tracksByBookId: Map<string, Track[]>,
  progressByBook: Map<string, AudiobookProgress>,
): BookContinueState | null {
  const bookIds = cycleBookIds(cycle);
  if (bookIds.size === 0) return null;

  let best: { state: BookContinueState; updatedAt: number } | null = null;

  for (const bookId of bookIds) {
    const progress = progressByBook.get(bookId);
    if (!progress || progress.bookId !== bookId) continue;

    const tracks = tracksByBookId.get(bookId) ?? [];
    if (resolveBookListenedMs(tracks, progress) <= 0) continue;

    const state = canContinueBookListening(bookId, tracks, progress);
    if (!state) continue;

    const updatedAt = Date.parse(progress.updatedAt);
    const ts = Number.isFinite(updatedAt) ? updatedAt : 0;
    if (!best || ts >= best.updatedAt) {
      best = { state, updatedAt: ts };
    }
  }

  return best?.state ?? null;
}

export function computeCycleCardState(
  cycle: Cycle,
  downloadedBookIds: Set<string>,
  tracksByBookId: Map<string, Track[]>,
  progressByBook: Map<string, AudiobookProgress>,
): CycleCardState {
  const bookIds = cycle.books.map((b) => b.id);
  const cycleTracks = bookIds.flatMap((bookId) => tracksByBookId.get(bookId) ?? []);
  const isDownloaded =
    cycleTracks.length > 0 && cycleTracks.every((track) => Boolean(track.localPath));
  const showDownload = !isDownloaded && cycleTracks.some((track) => !track.localPath);
  const showRemoveDownload = cycleTracks.some((track) => Boolean(track.localPath));

  let totalTracks = 0;
  let completedTracks = 0;
  for (const bookId of bookIds) {
    const bookTracks = tracksByBookId.get(bookId) ?? [];
    totalTracks += bookTracks.length;
    const progress = progressByBook.get(bookId);
    if (!progress) continue;
    const progressTrack = bookTracks.find((t) => t.id === progress.trackId);
    const progressSortOrder = progressTrack?.sortOrder ?? Infinity;
    for (const track of bookTracks) {
      if (track.id === progress.trackId && progress.positionMs >= (track.durationMs ?? 0) * 0.95) {
        completedTracks += 1;
      } else if (track.sortOrder < progressSortOrder) {
        completedTracks += 1;
      }
    }
  }

  const progressFraction = totalTracks > 0 ? completedTracks / totalTracks : null;
  const isListened = progressFraction != null && progressFraction >= 0.99;
  const continueState = resolveCycleContinueState(cycle, tracksByBookId, progressByBook);

  return { isDownloaded, progressFraction, continueState, showDownload, showRemoveDownload, isListened };
}

export function filterAndSortCycles(
  cycles: Cycle[],
  query: string,
  filter: LibraryFilter,
  downloadedBookIds: Set<string>,
  progressByBook: Map<string, AudiobookProgress>,
): Cycle[] {
  const q = query.trim().toLowerCase();
  let result = cycles.filter((cycle) => {
    if (!q) return true;
    if (cycle.title.toLowerCase().includes(q)) return true;
    return cycle.books.some(
      (b) => b.title.toLowerCase().includes(q) || (b.author ?? "").toLowerCase().includes(q),
    );
  });

  if (filter.contentFilter === "downloaded") {
    result = result.filter((cycle) =>
      cycle.books.every((b) => downloadedBookIds.has(b.id)),
    );
  }

  if (filter.sortOrder === "title") {
    result = [...result].sort((a, b) => a.title.localeCompare(b.title, "ru"));
  } else {
    result = [...result].sort((a, b) => {
      const aProgress = progressByBook.get(a.books[0]?.id ?? "");
      const bProgress = progressByBook.get(b.books[0]?.id ?? "");
      const aTime = aProgress ? Date.parse(aProgress.updatedAt) : 0;
      const bTime = bProgress ? Date.parse(bProgress.updatedAt) : 0;
      return bTime - aTime;
    });
  }

  return result;
}

export function isBookFullyDownloaded(bookId: string, tracksByBookId: Map<string, Track[]>): boolean {
  const bookTracks = tracksByBookId.get(bookId) ?? [];
  return bookTracks.length > 0 && bookTracks.every((t) => Boolean(t.localPath));
}

export function bookAuthorLabel(book: Book): string {
  return book.author ?? "";
}
