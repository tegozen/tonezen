import { describe, expect, it } from "vitest";
import {
  buildTracks,
  parseBookMeta,
  parseCycleMeta,
  storagePathForAudiobook,
  storagePathForMusic,
  trackTitleFromFilename,
} from "../src/parsers.js";

describe("parseCycleMeta", () => {
  it("parses valid cycle.json", () => {
    const meta = parseCycleMeta({
      title: "Horus Heresy",
      book_order: ["book-a", "book-b"],
    });
    expect(meta.title).toBe("Horus Heresy");
    expect(meta.book_order).toEqual(["book-a", "book-b"]);
  });

  it("throws on missing title", () => {
    expect(() => parseCycleMeta({ book_order: [] })).toThrow(/title/);
  });
});

describe("parseBookMeta", () => {
  it("parses audiobook metadata", () => {
    const meta = parseBookMeta(
      {
        title: "Fallen Angels",
        author: "Mike Lee",
        track_order: ["001-intro.mp3"],
      },
      "audiobook",
    );
    expect(meta.content_type).toBe("audiobook");
    expect(meta.author).toBe("Mike Lee");
  });

  it("defaults content type from path context", () => {
    const meta = parseBookMeta({ title: "Album", track_order: ["a.mp3"] }, "music");
    expect(meta.content_type).toBe("music");
  });
});

describe("buildTracks", () => {
  it("assigns sort order and titles", () => {
    const tracks = buildTracks(["001-intro.mp3", "002-chapter.mp3"]);
    expect(tracks[0].sortOrder).toBe(0);
    expect(tracks[1].title).toBe("chapter");
  });
});

describe("trackTitleFromFilename", () => {
  it("strips numeric prefix and extension", () => {
    expect(trackTitleFromFilename("001-intro.mp3")).toBe("intro");
  });
});

describe("storage paths", () => {
  it("builds audiobook path", () => {
    expect(storagePathForAudiobook("cycle", "book", "01.mp3")).toBe(
      "cycles/cycle/books/book/audio/01.mp3",
    );
  });

  it("builds music path", () => {
    expect(storagePathForMusic("album", "01.mp3")).toBe("music/album/audio/01.mp3");
  });
});
