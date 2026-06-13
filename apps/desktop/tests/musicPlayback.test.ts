import { describe, expect, it } from "vitest";
import { shouldRestartCurrentMusicTrack } from "../src/shared/musicPlayback.js";

describe("shouldRestartCurrentMusicTrack", () => {
  it("restarts when position is beyond threshold", () => {
    expect(shouldRestartCurrentMusicTrack(3001)).toBe(true);
  });

  it("skips to previous track near start", () => {
    expect(shouldRestartCurrentMusicTrack(3000)).toBe(false);
  });
});
