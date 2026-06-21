import { describe, expect, it } from "vitest";
import type { IndexedTrackRow } from "../src/db/indexedTracks.js";
import { shouldProbe } from "../src/shouldProbe.js";
import type { StorageObjectRow } from "../src/storage/listObjects.js";

const waveformPeaks = Array.from({ length: 64 }, (_, index) => index);

function indexedRow(overrides: Partial<IndexedTrackRow> = {}): IndexedTrackRow {
  return {
    storagePath: "music/track.mp3",
    checksum: "abc",
    sizeBytes: 1024,
    waveformPeaks,
    storageObjectUpdatedAt: new Date("2025-01-01T00:00:00.000Z"),
    title: "Track",
    artist: "Artist",
    durationMs: 60_000,
    ...overrides,
  };
}

function storageObject(overrides: Partial<StorageObjectRow> = {}): StorageObjectRow {
  return {
    name: "music/track.mp3",
    sizeBytes: 1024,
    updatedAt: new Date("2025-01-01T00:00:00.000Z"),
    ...overrides,
  };
}

describe("shouldProbe", () => {
  it("returns true for a new file", () => {
    expect(shouldProbe(storageObject(), undefined)).toBe(true);
  });

  it("returns true when size changed", () => {
    expect(shouldProbe(storageObject({ sizeBytes: 2048 }), indexedRow())).toBe(true);
  });

  it("returns true when storage_object_updated_at is missing", () => {
    expect(
      shouldProbe(
        storageObject({ updatedAt: new Date("2025-02-01T00:00:00.000Z") }),
        indexedRow({ storageObjectUpdatedAt: null }),
      ),
    ).toBe(true);
  });

  it("returns true when object updated_at is newer", () => {
    expect(
      shouldProbe(
        storageObject({ updatedAt: new Date("2025-03-01T00:00:00.000Z") }),
        indexedRow({ storageObjectUpdatedAt: new Date("2025-02-01T00:00:00.000Z") }),
      ),
    ).toBe(true);
  });

  it("returns true when display-name mapping is newer than indexed storage object", () => {
    expect(
      shouldProbe(
        storageObject({
          updatedAt: new Date("2025-02-01T00:00:00.000Z"),
          catalogUpdatedAt: new Date("2025-03-01T00:00:00.000Z"),
        }),
        indexedRow({ storageObjectUpdatedAt: new Date("2025-02-01T00:00:00.000Z") }),
      ),
    ).toBe(true);
  });

  it("returns true when waveform peaks are invalid", () => {
    expect(shouldProbe(storageObject(), indexedRow({ waveformPeaks: [1, 2, 3] }))).toBe(true);
  });

  it("returns false for unchanged indexed file", () => {
    const updatedAt = new Date("2025-01-01T00:00:00.000Z");
    expect(
      shouldProbe(storageObject({ updatedAt }), indexedRow({ storageObjectUpdatedAt: updatedAt })),
    ).toBe(false);
  });
});
