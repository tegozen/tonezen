import { describe, expect, it } from "vitest";
import {
  buildMusicTrackList,
  buildMusicTrackListForCatalogUpdate,
  MUSIC_LIBRARY_SLUG,
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
    coverUrl: null,
    cycleId: null,
  },
  {
    id: "b2",
    title: "Library",
    author: "Miyagi",
    contentType: "music",
    slug: MUSIC_LIBRARY_SLUG,
    coverUrl: null,
    cycleId: null,
  },
];

const tracks: Track[] = [
  { id: "t1", bookId: "b2", sortOrder: 1, title: "Two", filename: "2.mp3", artist: "Miyagi", durationMs: 2000 },
  { id: "t2", bookId: "b1", sortOrder: 0, title: "One", filename: "1.mp3", durationMs: 1000 },
  { id: "t3", bookId: "b2", sortOrder: 0, title: "Zero", filename: "0.mp3", artist: "Miyagi", durationMs: 3000, localPath: "/tmp/0.mp3" },
];

describe("buildMusicTrackListForCatalogUpdate", () => {
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
