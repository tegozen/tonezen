import { describe, expect, it } from "vitest";
import {
  buildMusicLibrary,
  buildTracks,
  isAudioFilename,
  parseBookMeta,
  parseCycleMeta,
  slugify,
  storagePathForAudiobook,
  storagePathForMusic,
  trackTitleFromFilename,
} from "../src/parsers.js";
import { parseTrackNumber } from "../src/mediaProbe.js";

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
    expect(storagePathForMusic("01.mp3")).toBe("music/01.mp3");
  });
});

describe("isAudioFilename", () => {
  it("accepts common audio extensions", () => {
    expect(isAudioFilename("track.mp3")).toBe(true);
    expect(isAudioFilename("track.flac")).toBe(true);
    expect(isAudioFilename("README.txt")).toBe(false);
  });
});

describe("slugify", () => {
  it("normalizes text into slugs", () => {
    expect(slugify("Artist Name - Album!")).toBe("artist-name-album");
  });
});

describe("parseTrackNumber", () => {
  it("parses track numbers from tag values", () => {
    expect(parseTrackNumber("3/12")).toBe(3);
    expect(parseTrackNumber("07")).toBe(7);
    expect(parseTrackNumber(undefined)).toBeNull();
  });
});

describe("buildMusicLibrary", () => {
  it("puts every file into one flat music library", () => {
    const library = buildMusicLibrary([
      {
        filename: "01-a.mp3",
        title: "Track A",
        artist: "Band",
        album: "Ignored Album Tag",
        trackNumber: 1,
      },
      {
        filename: "02-b.mp3",
        title: "Track B",
        artist: "Band",
        album: "Another Album",
        trackNumber: 2,
      },
    ]);
    expect(library).toHaveLength(1);
    expect(library[0].slug).toBe("music-library");
    expect(library[0].tracks.map((t) => t.title)).toEqual(["Track A", "Track B"]);
  });

  it("falls back to filename when metadata is missing", () => {
    const library = buildMusicLibrary([
      {
        filename: "01-my-song.mp3",
        title: null,
        artist: null,
        album: null,
        trackNumber: null,
      },
    ]);
    expect(library).toHaveLength(1);
    expect(library[0].slug).toBe("music-library");
    expect(library[0].tracks[0].title).toBe("my song");
  });
});
