import React from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { emptyDownloadQueueState } from "../src/shared/downloadQueueState.js";
import type { Book, Track } from "../src/shared/types.js";
import { BookDetailPage } from "../src/renderer/src/pages/BookDetailPage.js";

const emptyDownloadQueue = emptyDownloadQueueState();

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

function renderBookDetailPage(
  props: Omit<React.ComponentProps<typeof BookDetailPage>, "book" | "tracks">,
) {
  return renderToStaticMarkup(
    React.createElement(BookDetailPage, {
      book,
      tracks,
      downloadQueue: emptyDownloadQueue,
      ...props,
    }),
  );
}

describe("BookDetailPage", () => {
  it("hides continue action while the selected book is already playing", () => {
    const noop = vi.fn();

    const html = renderBookDetailPage({
      currentTrackId: "track-1",
      playbackPositionMs: 8_000,
      onBack: noop,
      onTrackClick: noop,
      onDownloadRequest: noop,
      onDownloadTrack: noop,
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
    });

    expect(html).not.toContain("Продолжить");
  });

  it("shows play action when book has no listening history", () => {
    const noop = vi.fn();

    const html = renderBookDetailPage({
      currentTrackId: null,
      playbackPositionMs: 0,
      onBack: noop,
      onTrackClick: noop,
      onDownloadRequest: noop,
      onDownloadTrack: noop,
      onToggleBookListened: noop,
      onRemoveBookDownloads: noop,
      onMarkTrackListened: noop,
      onRemoveTrackDownload: noop,
      onContinue: noop,
      savedTrackId: null,
      savedPositionMs: 0,
      isBookListened: false,
      hasDownloads: false,
      allDownloaded: false,
    });

    expect(html).toContain("Воспроизвести");
  });

  it("does not render a confirmation sheet before downloading a book", () => {
    const noop = vi.fn();

    const html = renderBookDetailPage({
      currentTrackId: null,
      playbackPositionMs: 0,
      onBack: noop,
      onTrackClick: noop,
      onDownloadRequest: noop,
      onDownloadTrack: noop,
      onToggleBookListened: noop,
      onRemoveBookDownloads: noop,
      onMarkTrackListened: noop,
      onRemoveTrackDownload: noop,
      onContinue: noop,
      savedTrackId: null,
      savedPositionMs: 0,
      isBookListened: false,
      hasDownloads: false,
      allDownloaded: false,
    });

    expect(html).not.toContain("Скачать аудиокнигу?");
    expect(html).not.toContain("Будет загружено несколько аудиофайлов на это устройство.");
  });
});
