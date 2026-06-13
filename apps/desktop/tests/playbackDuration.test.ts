import { describe, expect, it } from "vitest";
import { effectiveDurationMs } from "../src/shared/playbackDuration.js";

describe("effectiveDurationMs", () => {
  it("prefers audio duration when available", () => {
    expect(effectiveDurationMs(318, 300_000)).toBe(318_000);
  });

  it("falls back to track duration when audio is unknown", () => {
    expect(effectiveDurationMs(Number.NaN, 318_000)).toBe(318_000);
    expect(effectiveDurationMs(0, 318_000)).toBe(318_000);
  });

  it("returns zero when neither source is available", () => {
    expect(effectiveDurationMs(0, undefined)).toBe(0);
  });
});
