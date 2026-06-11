import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { rm } from "node:fs/promises";
import { scanContentRoot } from "../src/scanner.js";

const FIXTURE_ROOT = path.join(import.meta.dirname, "fixtures", "content");

describe("scanContentRoot", () => {
  beforeEach(async () => {
    await rm(FIXTURE_ROOT, { recursive: true, force: true });
    await mkdir(path.join(FIXTURE_ROOT, "cycles", "test-cycle", "books", "book-one", "audio"), {
      recursive: true,
    });
    await writeFile(
      path.join(FIXTURE_ROOT, "cycles", "test-cycle", "cycle.json"),
      JSON.stringify({ title: "Test Cycle", book_order: ["book-one"] }),
    );
    await writeFile(
      path.join(FIXTURE_ROOT, "cycles", "test-cycle", "books", "book-one", "book.json"),
      JSON.stringify({
        content_type: "audiobook",
        title: "Book One",
        author: "Author",
        track_order: ["001-intro.mp3"],
      }),
    );

    await mkdir(path.join(FIXTURE_ROOT, "music", "album-one", "audio"), { recursive: true });
    await writeFile(
      path.join(FIXTURE_ROOT, "music", "album-one", "album.json"),
      JSON.stringify({
        content_type: "music",
        title: "Album One",
        author: "Band",
        track_order: ["01-track.mp3"],
      }),
    );
  });

  afterEach(async () => {
    await rm(FIXTURE_ROOT, { recursive: true, force: true });
  });

  it("discovers cycles and music albums from fixture tree", async () => {
    const { cycles, musicAlbums } = await scanContentRoot(FIXTURE_ROOT);
    expect(cycles).toHaveLength(1);
    expect(cycles[0].slug).toBe("test-cycle");
    expect(cycles[0].books[0].tracks).toHaveLength(1);
    expect(musicAlbums).toHaveLength(1);
    expect(musicAlbums[0].contentType).toBe("music");
  });
});
