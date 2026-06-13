import { describe, expect, it } from "vitest";
import {
  beginBulkDownload,
  bulkProgressFraction,
  isBulkDownloading,
  isMusicDownloadActive,
  progressForTrack,
  updateBulkDownload,
} from "../src/shared/musicDownloadState.js";

describe("musicDownloadState", () => {
  it("tracks active download state", () => {
    const started = beginBulkDownload(
      { activeTrackId: null, trackProgress: null, bulkDownloaded: 0, bulkTotal: 0 },
      1,
      4,
    );
    expect(isBulkDownloading(started)).toBe(true);
    expect(isMusicDownloadActive(started)).toBe(true);
  });

  it("computes weighted bulk progress", () => {
    const state = updateBulkDownload(
      { activeTrackId: null, trackProgress: null, bulkDownloaded: 0, bulkTotal: 0 },
      1,
      4,
      "t2",
      0.5,
    );
    expect(bulkProgressFraction(state)).toBeCloseTo(0.375);
  });

  it("returns per-track progress", () => {
    const state = updateBulkDownload(
      { activeTrackId: null, trackProgress: null, bulkDownloaded: 0, bulkTotal: 0 },
      0,
      3,
      "t1",
      0.25,
    );
    expect(progressForTrack(state, "t1")).toBe(0.25);
    expect(progressForTrack(state, "t2")).toBeNull();
  });
});
