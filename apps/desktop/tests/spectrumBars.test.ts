import { describe, expect, it } from "vitest";
import { buildSpectrumBars, SPECTRUM_BAR_COUNT } from "../src/shared/spectrumBars.js";

describe("buildSpectrumBars", () => {
  it("builds stable bars for the same seed", () => {
    const first = buildSpectrumBars("track-miyagi-1");
    const second = buildSpectrumBars("track-miyagi-1");

    expect(first).toEqual(second);
    expect(first).toHaveLength(SPECTRUM_BAR_COUNT);
  });

  it("keeps values inside the expected visual range", () => {
    const bars = buildSpectrumBars("track-miyagi-2");

    expect(bars.every((bar) => bar.level >= 2 && bar.level <= 9)).toBe(true);
    expect(bars.every((bar) => bar.delayStep >= 0 && bar.delayStep <= 7)).toBe(true);
  });

  it("returns no bars for non-positive counts", () => {
    expect(buildSpectrumBars("track-miyagi-3", 0)).toEqual([]);
    expect(buildSpectrumBars("track-miyagi-3", -1)).toEqual([]);
  });
});
