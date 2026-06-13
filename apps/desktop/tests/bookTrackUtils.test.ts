import { describe, expect, it } from "vitest";
import { buildBookTrackProgress, canContinueBookListening, resolveChapterTrackState } from "../src/renderer/src/lib/bookTrackUtils.js";
import type { Track } from "../src/shared/types.js";

const tracks: Track[] = [
  { id: "t1", bookId: "b1", sortOrder: 0, title: "One", filename: "1.mp3", durationMs: 100_000 },
  { id: "t2", bookId: "b1", sortOrder: 1, title: "Two", filename: "2.mp3", durationMs: 100_000 },
];

describe("resolveChapterTrackState", () => {
  it("returns percent for in-progress track", () => {
    expect(resolveChapterTrackState(tracks[0], 0.05)).toEqual({
      listenProgress: 0.05,
      listenPercent: 5,
    });
  });

  it("returns completed state at 95 percent", () => {
    expect(resolveChapterTrackState(tracks[0], 0.95)).toEqual({
      listenProgress: 1,
      listenPercent: 100,
    });
  });
});

describe("buildBookTrackProgress", () => {
  it("uses live position for active track", () => {
    const map = buildBookTrackProgress(tracks, "t1", 5_000, "t1", 5_000);
    expect(map.get("t1")).toBe(0.05);
  });

  it("marks earlier tracks complete", () => {
    const map = buildBookTrackProgress(tracks, "t2", 0, null, 0);
    expect(map.get("t1")).toBe(1);
  });
});

describe("canContinueBookListening", () => {
  it("returns resume info for a partially listened chapter", () => {
    expect(
      canContinueBookListening("b1", tracks, { bookId: "b1", trackId: "t1", positionMs: 5_000 }),
    ).toEqual({
      trackTitle: "One",
      positionMs: 5_000,
    });
  });

  it("returns null when the book is fully listened", () => {
    expect(
      canContinueBookListening("b1", tracks, { bookId: "b1", trackId: "t2", positionMs: 95_000 }),
    ).toBeNull();
  });

  it("returns null without saved progress", () => {
    expect(canContinueBookListening("b1", tracks, null)).toBeNull();
  });

  it("returns null when progress belongs to another book", () => {
    expect(
      canContinueBookListening("b1", tracks, { bookId: "other", trackId: "t1", positionMs: 5_000 }),
    ).toBeNull();
  });
});
