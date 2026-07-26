import fs from "node:fs";
import {
  mergePriority,
  type DownloadPriority,
} from "@core/downloads/downloadQueuePolicy.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import type {
  DownloadAwaitResult,
  EnqueueDownloadRequest,
} from "@core/downloads/downloadQueueState.js";
import { isSafeStorageId, resolveTrackPartPath } from "@core/platform/safeLocalPaths.js";
import type { DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "../db/localDatabase.js";
import { queueKey } from "./asyncMutex.js";

const STATUS_QUEUED = "queued";

export interface TrackDownloadEnqueueHost {
  downloadsRoot: string;
  downloadManager: DownloadManager;
  failureCounts: Map<string, number>;
  userCancelledKeys: Set<string>;
  completeAwaiter: (key: string, result: DownloadAwaitResult) => void;
  refreshNotifierFromDb: () => void;
  startWorkerLocked: () => void;
}

export async function enqueueLocked(
  host: TrackDownloadEnqueueHost,
  request: EnqueueDownloadRequest,
  refreshNotifier = true,
): Promise<void> {
  if (!isSafeStorageId(request.bookId) || !isSafeStorageId(request.trackId)) return;
  const key = queueKey(request.bookId, request.trackId);
  if (LocalDatabase.resolveLocalTrackPath(request.bookId, request.trackId, host.downloadsRoot)) {
    host.failureCounts.delete(key);
    host.completeAwaiter(key, "COMPLETED");
    return;
  }

  const existing = LocalDatabase.get(request.bookId, request.trackId);
  const priority = existing
    ? mergePriority(existing.priority as DownloadPriority, request.priority)
    : request.priority;
  const partPath = resolveTrackPartPath(host.downloadsRoot, request.bookId, request.trackId);
  const partBytes =
    partPath && fs.existsSync(partPath) ? fs.statSync(partPath).size : existing?.bytesDownloaded ?? 0;

  const entity: DownloadQueueRow = {
    bookId: request.bookId,
    trackId: request.trackId,
    priority,
    batchId: request.batchId ?? existing?.batchId ?? null,
    enqueuedAt: existing?.enqueuedAt ?? request.enqueuedAt ?? Date.now(),
    title: request.title.trim() || existing?.title || request.trackId,
    subtitle: request.subtitle ?? existing?.subtitle ?? null,
    contentType: request.contentType,
    status: STATUS_QUEUED,
    bytesDownloaded: partBytes,
    totalBytes: existing?.totalBytes ?? null,
    tempPath: partPath,
  };
  LocalDatabase.upsert(entity);
  if (refreshNotifier) {
    host.refreshNotifierFromDb();
  }
  host.startWorkerLocked();
}

export async function cancelTrackLocked(
  host: TrackDownloadEnqueueHost,
  bookId: string,
  trackId: string,
): Promise<void> {
  const key = queueKey(bookId, trackId);
  host.userCancelledKeys.add(key);
  host.failureCounts.delete(key);
  LocalDatabase.delete(bookId, trackId);
  await host.downloadManager.deleteLocalTrack(bookId, trackId);
  host.completeAwaiter(key, "CANCELLED");
}

export async function persistPartProgress(
  downloadsRoot: string,
  bookId: string,
  trackId: string,
): Promise<void> {
  const partPath = resolveTrackPartPath(downloadsRoot, bookId, trackId);
  if (!partPath) return;
  const length = fs.existsSync(partPath) ? fs.statSync(partPath).size : 0;
  LocalDatabase.updateProgress(bookId, trackId, length, null, partPath);
}
