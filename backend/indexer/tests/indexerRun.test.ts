import { describe, expect, it, vi } from "vitest";
import { createFileProber } from "../src/fileProber.js";
import { scanStorageObjects } from "../src/scanner.js";
import type { StorageObjectRow } from "../src/storage/listObjects.js";

describe("incremental indexer run", () => {
  it("does not download when changed batch is empty", async () => {
    const download = vi.fn();
    const changed: StorageObjectRow[] = [];

    const { cycles, musicAlbums } = await scanStorageObjects(changed, {
      probeTags: async () => {
        download();
        return null;
      },
    });

    expect(cycles).toHaveLength(0);
    expect(musicAlbums).toHaveLength(0);
    expect(download).not.toHaveBeenCalled();
  });

  it("probes only changed files through FileProber", async () => {
    const download = vi.fn(async () => "/tmp/changed.mp3");
    const removeTemp = vi.fn(async () => undefined);
    const waveformPeaks = Array.from({ length: 64 }, (_, index) => index);

    const changed: StorageObjectRow[] = [
      {
        name: "music/changed.mp3",
        sizeBytes: 512,
        updatedAt: new Date("2025-06-01T00:00:00.000Z"),
      },
    ];

    const prober = createFileProber({
      objects: changed,
      indexed: new Map(),
      storage: { storageUrl: "http://x", bucket: "content", serviceRoleKey: "k" },
      download,
      removeTemp,
      analyzeAtPath: vi.fn(async () => ({
        sizeBytes: 512,
        checksum: "xyz",
        durationMs: 30_000,
        waveformPeaks,
      })),
      probeTagsAtPath: vi.fn(async () => ({
        title: "Changed",
        artist: "Band",
        album: null,
        trackNumber: 1,
        durationMs: 30_000,
      })),
    });

    const { musicAlbums } = await scanStorageObjects(changed, {
      probeTags: (path) => prober.probe(path).then((result) => result.tags),
    });

    expect(download).toHaveBeenCalledTimes(1);
    expect(musicAlbums[0]?.tracks[0]?.title).toBe("Changed");
    expect(prober.stats.probed).toBe(1);
  });
});
