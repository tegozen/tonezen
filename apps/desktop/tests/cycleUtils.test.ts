import { describe, expect, it } from "vitest";
import { buildTracksByBookId, isBookFullyDownloaded } from "../src/renderer/src/lib/cycleUtils.js";
import type { Track } from "../src/shared/types.js";

const tracks: Track[] = [
  {
    id: "t1",
    bookId: "b1",
    sortOrder: 1,
    title: "One",
    filename: "one.mp3",
    localPath: "/downloads/b1/t1.mp3",
  },
  {
    id: "t2",
    bookId: "b1",
    sortOrder: 2,
    title: "Two",
    filename: "two.mp3",
  },
  {
    id: "t3",
    bookId: "b2",
    sortOrder: 1,
    title: "Three",
    filename: "three.mp3",
    localPath: "/downloads/b2/t3.mp3",
  },
];

describe("buildTracksByBookId", () => {
  it("groups tracks by book id in one pass", () => {
    const map = buildTracksByBookId(tracks);
    expect(map.get("b1")?.map((t) => t.id)).toEqual(["t1", "t2"]);
    expect(map.get("b2")?.map((t) => t.id)).toEqual(["t3"]);
  });
});

describe("isBookFullyDownloaded", () => {
  it("uses grouped tracks without scanning the full library", () => {
    const map = buildTracksByBookId(tracks);
    expect(isBookFullyDownloaded("b1", map)).toBe(false);
    expect(isBookFullyDownloaded("b2", map)).toBe(true);
  });
});
