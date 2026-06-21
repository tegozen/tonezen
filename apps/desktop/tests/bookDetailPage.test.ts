import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Book, Track } from "../src/shared/types.js";
import { BookDetailPage } from "../src/renderer/src/pages/BookDetailPage.js";
import { strings } from "../src/renderer/src/i18n/strings.js";

const book: Book = {
  id: "book-1",
  title: "Book",
  slug: "book",
  contentType: "audiobook",
};

const tracks: Track[] = [
  {
    id: "track-1",
    bookId: "book-1",
    sortOrder: 0,
    title: "001",
    filename: "001.mp3",
    durationMs: 100_000,
    localPath: "C:\\audio\\001.mp3",
  },
];

describe("BookDetailPage", () => {
  it("hides continue action while the selected book is already playing", () => {
    const noop = vi.fn();

    const html = renderToStaticMarkup(
      BookDetailPage({
        book,
        tracks,
        currentTrackId: "track-1",
        playbackPositionMs: 8_000,
        showDownloadSheet: false,
        onBack: noop,
        onTrackClick: noop,
        onDownloadRequest: noop,
        onDownloadConfirm: noop,
        onDownloadDismiss: noop,
        onToggleBookListened: noop,
        onRemoveBookDownloads: noop,
        onMarkTrackListened: noop,
        onRemoveTrackDownload: noop,
        onContinue: noop,
        savedTrackId: "track-1",
        savedPositionMs: 6_000,
        isBookListened: false,
        hasDownloads: true,
        allDownloaded: true,
      }),
    );

    expect(html).not.toContain(strings.resume);
  });
});
