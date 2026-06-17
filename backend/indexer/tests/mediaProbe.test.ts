import { describe, expect, it } from "vitest";
import {
  metadataFromStoredIfUnchanged,
  normalizeWaveformBuckets,
  waveformPeaksFromPcm16,
} from "../src/mediaProbe.js";

const waveformPeaks = Array.from({ length: 64 }, (_, index) => index % 101);

describe("metadataFromStoredIfUnchanged", () => {
  it("reuses stored metadata when file size is unchanged", () => {
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000, waveform_peaks: waveformPeaks },
        1024,
      ),
    ).toEqual({
      sizeBytes: 1024,
      checksum: "abc",
      durationMs: 60000,
      waveformPeaks,
    });
  });

  it("returns null when checksum is missing or size changed", () => {
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: null, size_bytes: 1024, duration_ms: 60000, waveform_peaks: waveformPeaks },
        1024,
      ),
    ).toBeNull();
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000, waveform_peaks: waveformPeaks },
        2048,
      ),
    ).toBeNull();
  });

  it("returns null when waveform metadata is missing or invalid", () => {
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000 },
        1024,
      ),
    ).toBeNull();
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000, waveform_peaks: [0, 100] },
        1024,
      ),
    ).toBeNull();
  });
});

describe("waveform peak normalization", () => {
  it("normalizes RMS buckets to integer peaks", () => {
    expect(
      normalizeWaveformBuckets([
        { sumSquares: 0.25, samples: 1 },
        { sumSquares: 1, samples: 1 },
      ]),
    ).toEqual([50, 100]);
  });

  it("returns zero peaks for silent audio", () => {
    const peaks = waveformPeaksFromPcm16(Buffer.alloc(64 * 2), 64);

    expect(peaks).toEqual(Array.from({ length: 64 }, () => 0));
  });

  it("returns null for empty audio", () => {
    expect(waveformPeaksFromPcm16(Buffer.alloc(0), 64)).toBeNull();
  });

  it("creates 64 normalized peaks from PCM16 samples", () => {
    const input = Buffer.alloc(64 * 2);
    for (let index = 0; index < 64; index += 1) {
      input.writeInt16LE(Math.round(((index + 1) / 64) * 32767), index * 2);
    }

    const peaks = waveformPeaksFromPcm16(input, 64);

    expect(peaks).toHaveLength(64);
    expect(peaks?.at(-1)).toBe(100);
    expect(peaks?.every((peak) => Number.isInteger(peak) && peak >= 0 && peak <= 100)).toBe(true);
  });
});
