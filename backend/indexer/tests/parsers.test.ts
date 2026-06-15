import { describe, expect, it } from "vitest";
import {
  artistFromMusicFilename,
  buildAudiobookTracks,
  buildMusicLibrary,
  buildTracks,
  isAudioFilename,
  pickAudiobookAuthor,
  slugify,
  storagePathForAudiobook,
  storagePathForMusic,
  titleFromSlug,
  trackTitleFromFilename,
  trackTitleFromMusicFilename,
} from "../src/parsers.js";
import { parseTrackNumber } from "../src/mediaProbe.js";

describe("music filename parsing", () => {
  it("extracts artist and title from upload filename pattern", () => {
    expect(artistFromMusicFilename("Miyagi_-_Marlboro_65373356.mp3")).toBe("Miyagi");
    expect(trackTitleFromMusicFilename("Miyagi_-_Marlboro_65373356.mp3")).toBe("Marlboro");
    expect(artistFromMusicFilename("Miyagi_Amigo_-_Samaya_47829535.mp3")).toBe("Miyagi Amigo");
    expect(trackTitleFromMusicFilename("Miyagi_Amigo_-_Samaya_47829535.mp3")).toBe("Samaya");
  });
});

describe("titleFromSlug", () => {
  it("turns slug into display title", () => {
    expect(titleFromSlug("fallen-angels")).toBe("fallen angels");
    expect(titleFromSlug("horus-heresy")).toBe("horus heresy");
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

describe("buildAudiobookTracks", () => {
  it("uses tag title when present and falls back to filename", () => {
    const tracks = buildAudiobookTracks([
      { filename: "001-intro.mp3", title: "Introduction", artist: "Mike Lee" },
      { filename: "002-chapter.mp3", title: null, artist: null },
    ]);
    expect(tracks[0].title).toBe("Introduction");
    expect(tracks[1].title).toBe("chapter");
  });

  it("picks author from scanned files", () => {
    expect(
      pickAudiobookAuthor([
        { filename: "a.mp3", title: null, artist: null },
        { filename: "b.mp3", title: null, artist: "Mike Lee" },
      ]),
    ).toBe("Mike Lee");
  });
});

describe("storage paths", () => {
  it("builds audiobook path", () => {
    expect(storagePathForAudiobook("cycle", "book", "01.mp3")).toBe(
      "cycles/cycle/book/01.mp3",
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
    expect(library[0].author).toBeNull();
    expect(library[0].tracks.map((t) => t.artist)).toEqual(["Band", "Band"]);
  });

  it("parses per-track artist from filename when tags are missing", () => {
    const library = buildMusicLibrary([
      {
        filename: "Miyagi_-_Marlboro_65373356.mp3",
        title: "Marlboro",
        artist: null,
        album: null,
        trackNumber: null,
      },
      {
        filename: "Basta_GUF_-_V_sigaretnom_dymu_81300567.mp3",
        title: "В сигаретном дыму",
        artist: "Баста, GUF",
        album: null,
        trackNumber: null,
      },
    ]);
    expect(library[0].tracks[0].artist).toBe("Miyagi");
    expect(library[0].tracks[1].artist).toBe("Баста, GUF");
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
