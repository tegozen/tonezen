import { describe, expect, it } from "vitest";
import {
  resolveAudiobookPlaybackIntent,
  resolveAudiobookPlaybackStartMs,
} from "../src/shared/audiobookPlaybackIntent.js";
import type { AudiobookProgress, Track } from "../src/shared/types.js";

const tracks: Track[] = [
  { id: "ch1", bookId: "book-1", sortOrder: 0, title: "ch1", filename: "ch1.mp3", durationMs: 100_000 },
  { id: "ch2", bookId: "book-1", sortOrder: 1, title: "ch2", filename: "ch2.mp3", durationMs: 100_000 },
  { id: "ch3", bookId: "book-1", sortOrder: 2, title: "ch3", filename: "ch3.mp3", durationMs: 100_000 },
];

function progress(trackId: string, positionMs: number): AudiobookProgress {
  return {
    bookId: "book-1",
    trackId,
    positionMs,
    updatedAt: "2026-01-01T00:00:00.000Z",
  };
}

describe("resolveAudiobookPlaybackIntent", () => {
  it("resumes same chapter at saved position", () => {
    const intent = resolveAudiobookPlaybackIntent(tracks, progress("ch2", 50_000), tracks[1]);
    expect(intent).toEqual({ kind: "Resume", positionMs: 50_000 });
  });

  it("starts later chapter from zero", () => {
    const intent = resolveAudiobookPlaybackIntent(tracks, progress("ch2", 50_000), tracks[2]);
    expect(intent).toEqual({ kind: "StartFromZero" });
  });

  it("asks to confirm earlier chapter", () => {
    const intent = resolveAudiobookPlaybackIntent(tracks, progress("ch3", 10_000), tracks[0]);
    expect(intent).toEqual({
      kind: "ConfirmEarlierChapter",
      savedTrackId: "ch3",
      savedPositionMs: 10_000,
    });
  });

  it("starts from zero without saved progress", () => {
    expect(resolveAudiobookPlaybackIntent(tracks, null, tracks[1])).toEqual({ kind: "StartFromZero" });
  });
});

describe("resolveAudiobookPlaybackStartMs", () => {
  it("restarts completed chapter from beginning", () => {
    expect(resolveAudiobookPlaybackStartMs(progress("ch2", 96_000), tracks[1])).toBe(0);
  });
});
