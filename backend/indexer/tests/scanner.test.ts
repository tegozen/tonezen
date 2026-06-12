import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { rm } from "node:fs/promises";
import { scanContentRoot } from "../src/scanner.js";

const FIXTURE_ROOT = path.join(import.meta.dirname, "fixtures", "content");

describe("scanContentRoot", () => {
  beforeEach(async () => {
    await rm(FIXTURE_ROOT, { recursive: true, force: true });
    await mkdir(path.join(FIXTURE_ROOT, "cycles", "test-cycle", "book-one"), {
      recursive: true,
    });
    await writeFile(
      path.join(FIXTURE_ROOT, "cycles", "test-cycle", "book-one", "001-intro.mp3"),
      Buffer.alloc(0),
    );

    await mkdir(path.join(FIXTURE_ROOT, "cycles", "test-cycle", "book-two"), {
      recursive: true,
    });
    await writeFile(
      path.join(FIXTURE_ROOT, "cycles", "test-cycle", "book-two", "002-part.mp3"),
      Buffer.alloc(0),
    );

    await mkdir(path.join(FIXTURE_ROOT, "music"), { recursive: true });
    await writeFile(path.join(FIXTURE_ROOT, "music", "01-track.mp3"), Buffer.alloc(0));
  });

  afterEach(async () => {
    await rm(FIXTURE_ROOT, { recursive: true, force: true });
  });

  it("discovers audiobook tracks stored as Supabase object directories", async () => {
    await rm(path.join(FIXTURE_ROOT, "cycles", "storage-cycle", "book-a", "001-intro.mp3"), {
      force: true,
    });
    await mkdir(
      path.join(FIXTURE_ROOT, "cycles", "storage-cycle", "book-a", "001-intro.mp3"),
      { recursive: true },
    );
    await writeFile(
      path.join(
        FIXTURE_ROOT,
        "cycles",
        "storage-cycle",
        "book-a",
        "001-intro.mp3",
        "object-id",
      ),
      Buffer.from("audio"),
    );

    const { cycles } = await scanContentRoot(FIXTURE_ROOT);
    const cycle = cycles.find((item) => item.slug === "storage-cycle");
    expect(cycle).toBeDefined();
    expect(cycle?.books[0].tracks[0].filename).toBe("001-intro.mp3");
  });

  it("discovers cycles from directories and sorts books and tracks by name", async () => {
    const { cycles, musicAlbums } = await scanContentRoot(FIXTURE_ROOT);
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
});
