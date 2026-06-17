import {
  analyzeAudioFileAtPath,
  metadataFromStoredIfUnchanged,
  probeAudioTags,
  type AudioTags,
  type FileMetadata,
} from "./mediaProbe.js";
import type { IndexedTrackRow } from "./db/indexedTracks.js";
import { shouldProbe } from "./shouldProbe.js";
import {
  downloadObjectToTemp,
  removeTempFile,
  type StorageDownloadConfig,
} from "./storage/download.js";
import type { StorageObjectRow } from "./storage/listObjects.js";

export interface ProbedFile {
  tags: AudioTags | null;
  metadata: FileMetadata | null;
  downloaded: boolean;
}

export interface FileProberStats {
  probed: number;
  skipped: number;
}

export interface FileProber {
  probe(storagePath: string): Promise<ProbedFile>;
  stats: FileProberStats;
}

export interface CreateFileProberOptions {
  objects: StorageObjectRow[];
  indexed: Map<string, IndexedTrackRow>;
  storage: StorageDownloadConfig;
  concurrency?: number;
  download?: typeof downloadObjectToTemp;
  removeTemp?: typeof removeTempFile;
  analyzeAtPath?: typeof analyzeAudioFileAtPath;
  probeTagsAtPath?: typeof probeAudioTags;
}

function tagsFromIndexedRow(row: IndexedTrackRow): AudioTags {
  return {
    title: row.title,
    artist: row.artist,
    album: null,
    trackNumber: null,
    durationMs: row.durationMs,
  };
}

function metadataFromIndexedRow(row: IndexedTrackRow, sizeBytes: number): FileMetadata | null {
  return metadataFromStoredIfUnchanged(
    {
      checksum: row.checksum,
      size_bytes: row.sizeBytes,
      duration_ms: row.durationMs,
      waveform_peaks: row.waveformPeaks,
    },
    sizeBytes,
  );
}

export function createFileProber(options: CreateFileProberOptions): FileProber {
  const objectByPath = new Map(options.objects.map((object) => [object.name, object]));
  const cache = new Map<string, Promise<ProbedFile>>();
  const stats: FileProberStats = { probed: 0, skipped: 0 };

  const download = options.download ?? downloadObjectToTemp;
  const removeTemp = options.removeTemp ?? removeTempFile;
  const analyzeAtPath = options.analyzeAtPath ?? analyzeAudioFileAtPath;
  const probeTagsAtPath = options.probeTagsAtPath ?? probeAudioTags;

  async function probeOnce(storagePath: string): Promise<ProbedFile> {
    const object = objectByPath.get(storagePath);
    const indexed = options.indexed.get(storagePath);

    if (object && indexed && !shouldProbe(object, indexed)) {
      const sizeBytes = object.sizeBytes ?? indexed.sizeBytes ?? 0;
      stats.skipped += 1;
      return {
        tags: tagsFromIndexedRow(indexed),
        metadata: metadataFromIndexedRow(indexed, sizeBytes),
        downloaded: false,
      };
    }

    stats.probed += 1;
    let tempPath: string | null = null;
    try {
      tempPath = await download(storagePath, options.storage);
      const tags = await probeTagsAtPath(tempPath);
      const metadata = await analyzeAtPath(tempPath, {
        knownDurationMs: tags?.durationMs ?? indexed?.durationMs ?? undefined,
      });
      const cachedSize = object?.sizeBytes;
      const normalizedMetadata =
        metadata && cachedSize != null && metadata.sizeBytes !== cachedSize
          ? { ...metadata, sizeBytes: cachedSize }
          : metadata;

      return {
        tags,
        metadata: normalizedMetadata,
        downloaded: true,
      };
    } catch {
      return { tags: null, metadata: null, downloaded: true };
    } finally {
      if (tempPath) {
        await removeTemp(tempPath);
      }
    }
  }

  return {
    stats,
    probe(storagePath: string): Promise<ProbedFile> {
      const pending = cache.get(storagePath);
      if (pending) {
        return pending;
      }

      const result = probeOnce(storagePath);
      cache.set(storagePath, result);
      return result;
    },
  };
}

export async function warmUpProber(
  prober: FileProber,
  storagePaths: string[],
  concurrency: number,
): Promise<void> {
  if (storagePaths.length === 0) {
    return;
  }

  let nextIndex = 0;
  const workerCount = Math.min(Math.max(1, concurrency), storagePaths.length);

  async function worker(): Promise<void> {
    while (nextIndex < storagePaths.length) {
      const index = nextIndex;
      nextIndex += 1;
      await prober.probe(storagePaths[index]);
    }
  }

  await Promise.all(Array.from({ length: workerCount }, () => worker()));
}
