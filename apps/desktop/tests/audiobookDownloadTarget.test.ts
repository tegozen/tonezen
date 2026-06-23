import { describe, expect, it } from "vitest";
import { nextAudiobookDownloadRequest } from "../src/shared/audiobookDownloadTarget.js";
import type { Book, Track } from "../src/shared/types.js";

const book: Book = {
  id: "book-1",
  slug: "book-1",
  contentType: "audiobook",
  title: "Book",
};

const tracks: Track[] = [
  {
    id: "track-1",
    bookId: "book-1",
    sortOrder: 0,
    title: "001",
    filename: "001.mp3",
    localPath: "C:\\audio\\001.mp3",
  },
  {
    id: "track-2",
    bookId: "book-1",
    sortOrder: 1,
    title: "002",
    filename: "002.mp3",
  },
  {
    id: "track-3",
    bookId: "book-1",
    sortOrder: 2,
    title: "003",
    filename: "003.mp3",
  },
];

describe("audiobookDownloadTarget", () => {
  it("queues only the next missing audiobook track after the current one", () => {
    const request = nextAudiobookDownloadRequest({
      book,
      tracks,
      currentTrackId: "track-1",
      savedTrackId: null,
    });

    expect(request).toMatchObject({
      bookId: "book-1",
      trackId: "track-2",
      priority: "USER",
      title: "002",
      subtitle: "Book",
      contentType: "audiobook",
    });
  });

  it("does not wrap to earlier missing tracks while playback has a current track", () => {
    const request = nextAudiobookDownloadRequest({
      book,
      tracks: [
        { ...tracks[0], localPath: undefined },
        { ...tracks[1], localPath: "C:\\audio\\002.mp3" },
        { ...tracks[2], localPath: "C:\\audio\\003.mp3" },
      ],
      currentTrackId: "track-2",
      savedTrackId: null,
    });

    expect(request).toBeNull();
  });
});
