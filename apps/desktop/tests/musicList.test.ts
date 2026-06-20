import { afterEach, describe, expect, it, vi } from "vitest";
import {
  buildMusicTrackList,
  buildMusicTrackListForCatalogUpdate,
  musicQueueWindowFrom,
  MUSIC_LIBRARY_SLUG,
  MUSIC_QUEUE_INITIAL_WINDOW_SIZE,
  nextMusicQueueWindow,
  refreshMusicTrackListDownloadState,
  resolveMusicLibraryBooks,
} from "../src/shared/musicList.js";
import type { Book, Track } from "../src/shared/types.js";

const books: Book[] = [
  {
    id: "b1",
    title: "Album",
    author: "Artist",
    contentType: "music",
    slug: "album",
  },
  {
    id: "b2",
    title: "Library",
    author: "Miyagi",
    contentType: "music",
    slug: MUSIC_LIBRARY_SLUG,
  },
];

const tracks: Track[] = [
  { id: "t1", bookId: "b2", sortOrder: 1, title: "Two", filename: "2.mp3", artist: "Miyagi", durationMs: 2000 },
  { id: "t2", bookId: "b1", sortOrder: 0, title: "One", filename: "1.mp3", durationMs: 1000 },
  { id: "t3", bookId: "b2", sortOrder: 0, title: "Zero", filename: "0.mp3", artist: "Miyagi", durationMs: 3000, localPath: "/tmp/0.mp3" },
];

describe("buildMusicTrackListForCatalogUpdate", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("prefers music-library slug books", () => {
    const list = buildMusicTrackList(books, tracks);
    expect(list.map((track) => track.trackId)).toEqual(["t3", "t1"]);
    expect(resolveMusicLibraryBooks(books).map((book) => book.id)).toEqual(["b2"]);
  });

  it("shuffles only on first build before playback starts", () => {
    const first = buildMusicTrackListForCatalogUpdate([], books, tracks, false);
    const second = buildMusicTrackListForCatalogUpdate(first, books, tracks, false);
    expect(second.map((track) => track.trackId)).toEqual(first.map((track) => track.trackId));
  });

  it("keeps catalog order after playback started when list was empty", () => {
    const built = buildMusicTrackListForCatalogUpdate([], books, tracks, true);
    expect(built.map((track) => track.trackId)).toEqual(["t3", "t1"]);
  });

  it("preserves order when catalog gains tracks", () => {
    const stale = [
      {
        trackId: "t3",
        trackTitle: "Zero",
        artist: "Miyagi",
        albumTitle: "Library",
        bookId: "b2",
        durationMs: 3000,
        isDownloaded: false,
      },
    ];
    const updated = buildMusicTrackListForCatalogUpdate(stale, books, tracks, false);
    expect(updated.map((track) => track.trackId)).toEqual(["t3", "t1"]);
  });

  it("rebuilds list when catalog gains tracks", () => {
    const stale = buildMusicTrackList(books, tracks.slice(0, 1));
    const updated = buildMusicTrackListForCatalogUpdate(stale, books, tracks, false);
    expect(updated.map((track) => track.trackId).sort()).toEqual(["t1", "t3"]);
    expect(updated).toHaveLength(2);
  });

  it("appends backend tracks as a separately shuffled suffix", () => {
    vi.spyOn(Math, "random")
      .mockReturnValueOnce(0)
      .mockReturnValueOnce(0);
    const newTracks: Track[] = [
      ...tracks,
      { id: "t4", bookId: "b2", sortOrder: 2, title: "Three", filename: "3.mp3" },
      { id: "t5", bookId: "b2", sortOrder: 3, title: "Four", filename: "4.mp3" },
      { id: "t6", bookId: "b2", sortOrder: 4, title: "Five", filename: "5.mp3" },
    ];
    const existing = [
      {
        trackId: "t1",
        trackTitle: "Two",
        artist: "Miyagi",
        albumTitle: "Library",
        bookId: "b2",
        durationMs: 2000,
        isDownloaded: false,
      },
      {
        trackId: "t3",
        trackTitle: "Zero",
        artist: "Miyagi",
        albumTitle: "Library",
        bookId: "b2",
        durationMs: 3000,
        isDownloaded: true,
      },
    ];

    const updated = buildMusicTrackListForCatalogUpdate(existing, books, newTracks, false);

    expect(updated.map((track) => track.trackId)).toEqual(["t1", "t3", "t5", "t6", "t4"]);
  });

  it("rebuilds list when track metadata changes", () => {
    const stale = [
      {
        trackId: "t3",
        trackTitle: "Miyagi__Ugly_Name",
        artist: "Music",
        albumTitle: "Library",
        bookId: "b2",
        durationMs: 3000,
        isDownloaded: false,
      },
    ];
    const updated = buildMusicTrackListForCatalogUpdate(stale, books, [tracks[2]], false);
    expect(updated).toHaveLength(1);
    expect(updated[0]?.trackTitle).toBe("Zero");
    expect(updated[0]?.artist).toBe("Miyagi");
  });

  it("uses per-track artist over book author", () => {
    const mixedBooks: Book[] = [{ ...books[1], author: "Баста" }];
    const mixedTracks: Track[] = [
      { id: "t1", bookId: "b2", sortOrder: 0, title: "Marlboro", filename: "a.mp3", artist: "Miyagi" },
      { id: "t4", bookId: "b2", sortOrder: 1, title: "Весна", filename: "b.mp3", artist: "Баста, GUF" },
    ];
    const list = buildMusicTrackList(mixedBooks, mixedTracks);
    expect(list.map((track) => track.artist)).toEqual(["Miyagi", "Баста, GUF"]);
  });
});

describe("refreshMusicTrackListDownloadState", () => {
  it("preserves order while updating download flags", () => {
    const list = buildMusicTrackList(books, tracks).slice().reverse();
    const refreshed = refreshMusicTrackListDownloadState(list, books, tracks);
    expect(refreshed.map((track) => track.trackId)).toEqual(list.map((track) => track.trackId));
    expect(refreshed.find((track) => track.trackId === "t3")?.isDownloaded).toBe(true);
  });
});

describe("music queue windows", () => {
  const queueTracks = Array.from({ length: 30 }, (_, index) => ({
    trackId: `t${index}`,
    trackTitle: `Track ${index}`,
    artist: "Artist",
    albumTitle: "Album",
    bookId: "b2",
    isDownloaded: index % 2 === 0,
  }));

  it("starts at selected track, wraps, and caps the visible queue", () => {
    const window = musicQueueWindowFrom(queueTracks, "t10");
    expect(window).toHaveLength(MUSIC_QUEUE_INITIAL_WINDOW_SIZE);
    expect(window[0]?.trackId).toBe("t10");
    expect(window.at(-1)?.trackId).toBe("t3");
  });

  it("returns the next append window without materialized duplicates", () => {
    const materializedIds = new Set(Array.from({ length: 24 }, (_, offset) => `t${offset + 6}`));
    const window = nextMusicQueueWindow(queueTracks, "t29", materializedIds);
    expect(window.map((track) => track.trackId)).toEqual(["t0", "t1", "t2", "t3", "t4", "t5"]);
  });
});
