import { describe, expect, it } from "vitest";
import {
  normalizeWaveformPeaks,
  parseWaveformPeaksJson,
  serializeWaveformPeaks,
} from "../src/shared/waveformPeaks.js";

describe("waveform peak helpers", () => {
  const validPeaks = Array.from({ length: 64 }, (_, index) => index);

  it("normalizes valid 64-point integer arrays", () => {
    expect(normalizeWaveformPeaks(validPeaks)).toEqual(validPeaks);
  });

  it("falls back to null for missing or invalid waveform arrays", () => {
    expect(normalizeWaveformPeaks(null)).toBeNull();
    expect(normalizeWaveformPeaks([])).toBeNull();
    expect(normalizeWaveformPeaks(Array.from({ length: 64 }, () => 101))).toBeNull();
    expect(normalizeWaveformPeaks(Array.from({ length: 64 }, () => 0.5))).toBeNull();
  });

  it("serializes and parses waveform JSON for sqlite storage", () => {
    const json = serializeWaveformPeaks(validPeaks);

    expect(json).toBe(JSON.stringify(validPeaks));
    expect(parseWaveformPeaksJson(json)).toEqual(validPeaks);
    expect(parseWaveformPeaksJson("[0,100]")).toBeNull();
    expect(parseWaveformPeaksJson("not-json")).toBeNull();
  });
});
