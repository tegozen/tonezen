import type { Book, Cycle, Track } from "@core/types.js";

export interface NextPlaybackTarget {
  track: Track | null;
  book: Book | null;
  isNextBookInCycle: boolean;
}

export class CyclePlaybackResolver {
  nextInBook(currentTrack: Track, tracks: Track[]): Track | null {
    const sorted = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const index = sorted.findIndex((t) => t.id === currentTrack.id);
    if (index < 0 || index >= sorted.length - 1) return null;
    return sorted[index + 1];
  }

  previousInBook(currentTrack: Track, tracks: Track[]): Track | null {
    const sorted = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const index = sorted.findIndex((t) => t.id === currentTrack.id);
    if (index <= 0) return null;
    return sorted[index - 1];
  }

  nextInCycle(
    currentBook: Book,
    currentTrack: Track,
    cycle: Cycle,
    booksBySlug: Map<string, Book>,
    tracksByBookId: Map<string, Track[]>,
  ): NextPlaybackTarget {
    const bookTracks = [...(tracksByBookId.get(currentBook.id) ?? [])].sort(
      (a, b) => a.sortOrder - b.sortOrder,
    );
    const nextTrack = this.nextInBook(currentTrack, bookTracks);
    if (nextTrack) {
      return { track: nextTrack, book: currentBook, isNextBookInCycle: false };
    }
    if (currentBook.contentType !== "audiobook") {
      return { track: null, book: null, isNextBookInCycle: false };
    }
    const bookIndex = cycle.bookOrder.indexOf(currentBook.slug);
    if (bookIndex < 0 || bookIndex >= cycle.bookOrder.length - 1) {
      return { track: null, book: null, isNextBookInCycle: false };
    }
    const nextBook = booksBySlug.get(cycle.bookOrder[bookIndex + 1]);
    if (!nextBook) return { track: null, book: null, isNextBookInCycle: false };
    const nextBookTracks = [...(tracksByBookId.get(nextBook.id) ?? [])].sort(
      (a, b) => a.sortOrder - b.sortOrder,
    );
    const firstTrack = nextBookTracks[0] ?? null;
    return {
      track: firstTrack,
      book: nextBook,
      isNextBookInCycle: firstTrack != null,
    };
  }
}
