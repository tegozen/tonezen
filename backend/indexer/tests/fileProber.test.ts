import { describe, expect, it, vi } from "vitest";
import { createFileProber } from "../src/fileProber.js";
import type { IndexedTrackRow } from "../src/db/indexedTracks.js";
import type { StorageObjectRow } from "../src/storage/listObjects.js";

const waveformPeaks = Array.from({ length: 64 }, (_, index) => index);

describe("createFileProber", () => {
  it("reuses indexed metadata without downloading", async () => {
    const download = vi.fn();
    const indexed = new Map<string, IndexedTrackRow>([
      [
        "music/track.mp3",
        {
          storagePath: "music/track.mp3",
          checksum: "abc",
          sizeBytes: 1024,
          waveformPeaks,
          storageObjectUpdatedAt: new Date("2025-01-01T00:00:00.000Z"),
          title: "Cached Title",
          artist: "Cached Artist",
          durationMs: 120_000,
        },
      ],
    ]);
    const objects: StorageObjectRow[] = [
      {
        name: "music/track.mp3",
        sizeBytes: 1024,
        updatedAt: new Date("2025-01-01T00:00:00.000Z"),
      },
    ];

    const prober = createFileProber({
      objects,
      indexed,
      storage: { storageUrl: "http://x", bucket: "content", serviceRoleKey: "k" },
      download,
    });

    const first = await prober.probe("music/track.mp3");
    const second = await prober.probe("music/track.mp3");

    expect(download).not.toHaveBeenCalled();
    expect(first.downloaded).toBe(false);
    expect(first.tags?.title).toBe("Cached Title");
    expect(first.metadata?.checksum).toBe("abc");
    expect(second).toBe(first);
    expect(prober.stats.skipped).toBe(1);
    expect(prober.stats.probed).toBe(0);
  });

  it("downloads once and shares run cache between probes", async () => {
    const download = vi.fn(async () => "/tmp/file.mp3");
    const removeTemp = vi.fn(async () => undefined);
    const probeTagsAtPath = vi.fn(async () => ({
      title: "Tag Title",
      artist: "Tag Artist",
      album: null,
      trackNumber: null,
      durationMs: 90_000,
    }));
    const analyzeAtPath = vi.fn(async () => ({
      sizeBytes: 2048,
      checksum: "def",
      durationMs: 90_000,
      waveformPeaks,
    }));

    const objects: StorageObjectRow[] = [
      {
        name: "music/new.mp3",
        sizeBytes: 2048,
        updatedAt: new Date("2025-06-01T00:00:00.000Z"),
      },
    ];

    const prober = createFileProber({
      objects,
      indexed: new Map(),
      storage: { storageUrl: "http://x", bucket: "content", serviceRoleKey: "k" },
      download,
      removeTemp,
      probeTagsAtPath,
      analyzeAtPath,
    });

    await prober.probe("music/new.mp3");
    await prober.probe("music/new.mp3");

    expect(download).toHaveBeenCalledTimes(1);
    expect(probeTagsAtPath).toHaveBeenCalledTimes(1);
    expect(analyzeAtPath).toHaveBeenCalledTimes(1);
    expect(removeTemp).toHaveBeenCalledTimes(1);
    expect(prober.stats.probed).toBe(1);
  });
});
