import { describe, expect, it } from "vitest";
import { shouldProbe } from "../src/shouldProbe.js";
import type { IndexedTrackRow } from "../src/db/indexedTracks.js";
import type { StorageObjectRow } from "../src/storage/listObjects.js";

describe("catalog upsert safeguards", () => {
  it("preserves author when scan author is null and row unchanged", () => {
    const object: StorageObjectRow = {
      name: "cycles/saga/book-a/001-intro.mp3",
      sizeBytes: 1000,
      updatedAt: new Date("2025-01-01T00:00:00.000Z"),
    };
    const row: IndexedTrackRow = {
      storagePath: object.name,
      checksum: "abc",
      sizeBytes: 1000,
      waveformPeaks: Array.from({ length: 64 }, (_, index) => index),
      storageObjectUpdatedAt: new Date("2025-01-01T00:00:00.000Z"),
      title: "Intro",
      artist: "Stored Author",
      durationMs: 10_000,
    };

    expect(shouldProbe(object, row)).toBe(false);
    expect(row.artist).toBe("Stored Author");
  });
});
