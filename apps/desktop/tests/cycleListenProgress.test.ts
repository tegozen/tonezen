import { describe, expect, it } from "vitest";
import { resolveBookListenedMs } from "../src/shared/cycleListenProgress.js";
import type { AudiobookProgress, Track } from "../src/shared/types.js";

const tracks: Track[] = [
  { id: "t1", bookId: "b1", sortOrder: 0, title: "One", filename: "1.mp3", durationMs: 100_000 },
  { id: "t2", bookId: "b1", sortOrder: 1, title: "Two", filename: "2.mp3", durationMs: 100_000 },
];

describe("resolveBookListenedMs", () => {
  it("sums completed chapters and current position", () => {
    const progress: AudiobookProgress = {
      bookId: "b1",
      trackId: "t2",
      positionMs: 40_000,
      updatedAt: "2026-01-01T00:00:00.000Z",
    };
    expect(resolveBookListenedMs(tracks, progress)).toBe(140_000);
  });

  it("returns zero when track does not belong to the book", () => {
    const progress: AudiobookProgress = {
      bookId: "b1",
      trackId: "t1",
      positionMs: 10_000,
      updatedAt: "2026-01-01T00:00:00.000Z",
    };
    expect(resolveBookListenedMs([{ ...tracks[0], bookId: "other" }], progress)).toBe(0);
  });
});
