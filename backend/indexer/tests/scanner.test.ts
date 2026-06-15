import { describe, expect, it } from "vitest";
import { scanStorageObjects } from "../src/scanner.js";

describe("scanStorageObjects", () => {
  it("builds cycles and music library from flat storage object paths", async () => {
    const { cycles, musicAlbums } = await scanStorageObjects([
      { name: "cycles/test-cycle/book-one/001-intro.mp3" },
      { name: "cycles/test-cycle/book-two/002-part.mp3" },
      { name: "music/01-track.mp3" },
      { name: "cycles/test-cycle/book-one/.gitkeep" },
      { name: "music/readme.txt" },
    ]);

    expect(cycles).toHaveLength(1);
    expect(cycles[0].slug).toBe("test-cycle");
    expect(cycles[0].title).toBe("test cycle");
    expect(cycles[0].bookOrder).toEqual(["book-one", "book-two"]);
    expect(cycles[0].books[0].title).toBe("book one");
    expect(cycles[0].books[0].tracks[0].filename).toBe("001-intro.mp3");
    expect(cycles[0].books[1].tracks[0].filename).toBe("002-part.mp3");
    expect(musicAlbums).toHaveLength(1);
    expect(musicAlbums[0].slug).toBe("music-library");
    expect(musicAlbums[0].contentType).toBe("music");
    expect(musicAlbums[0].tracks).toHaveLength(1);
    expect(musicAlbums[0].tracks[0].filename).toBe("01-track.mp3");
  });

  it("uses probeTags callback for ID3 metadata", async () => {
    const { cycles, musicAlbums } = await scanStorageObjects(
      [
        { name: "cycles/saga/book-a/001-intro.mp3" },
        { name: "music/01-track.mp3" },
      ],
      {
        probeTags: async (storagePath) => {
          if (storagePath.endsWith("001-intro.mp3")) {
            return {
              title: "Intro Tag",
              artist: "Author Name",
              album: null,
              trackNumber: null,
              durationMs: 120_000,
            };
          }
          return {
            title: "Song Tag",
            artist: "Band",
            album: "Album",
            trackNumber: 3,
            durationMs: 200_000,
          };
        },
      },
    );

    expect(cycles[0].books[0].author).toBe("Author Name");
    expect(cycles[0].books[0].tracks[0].title).toBe("Intro Tag");
    expect(cycles[0].books[0].tracks[0].durationMs).toBe(120_000);
    expect(musicAlbums[0].tracks[0].title).toBe("Song Tag");
    expect(musicAlbums[0].tracks[0].sortOrder).toBe(0);
  });

  it("ignores nested music paths and malformed cycle paths", async () => {
    const { cycles, musicAlbums } = await scanStorageObjects([
      { name: "music/nested/01-track.mp3" },
      { name: "cycles/only-cycle/001-intro.mp3" },
      { name: "cycles/cycle/book/extra/deep.mp3" },
    ]);

    expect(cycles).toHaveLength(0);
    expect(musicAlbums).toHaveLength(0);
  });
});
