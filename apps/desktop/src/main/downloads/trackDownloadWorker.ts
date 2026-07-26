import fs from "node:fs";
import type { BrowserWindow } from "electron";
import type { DownloadAwaitResult, DownloadQueueState } from "@core/downloads/downloadQueueState.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import { resolveTrackPartPath } from "@core/platform/safeLocalPaths.js";
import { DownloadCancelledError, type DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "../db/localDatabase.js";
import type { SessionService } from "../session/sessionService.js";
import type { DiagnosticErrorEntry } from "../app/diagnosticsLog.js";
import { AsyncMutex, delay, queueKey } from "./asyncMutex.js";
import { pickNextQueueRow, type BulkCounters } from "./downloadQueueStateBuilder.js";
import { persistPartProgress } from "./trackDownloadEnqueue.js";
import { notifyCatalogUpdated, reportDownloadFailure } from "./trackDownloadNotify.js";

const MAX_DOWNLOAD_FAILURES = 3;

type DownloadFailureLogger = (entry: DiagnosticErrorEntry) => void | Promise<unknown>;

export interface TrackDownloadWorkerHost {
  mutex: AsyncMutex;
  downloadsRoot: string;
  downloadManager: DownloadManager;
  sessionService: SessionService;
  logDownloadFailure?: DownloadFailureLogger;
  mainWindow: () => BrowserWindow | null;
  pausedForNetwork: () => boolean;
  getBulk: () => BulkCounters;
  userCancelledKeys: Set<string>;
  failureCounts: Map<string, number>;
  patchState: (patch: Partial<DownloadQueueState>) => void;
  broadcastState: () => void;
  refreshNotifierFromDb: () => void;
  addCompletedHistory: (entity: DownloadQueueRow) => void;
  completeAwaiter: (key: string, result: DownloadAwaitResult) => void;
  stopWorkerIfIdle: () => void;
  setWorkerRunning: (running: boolean) => void;
}

/** Runs outside the mutex for network IO; only short critical sections use mutex. */
export async function runTrackDownloadWorker(host: TrackDownloadWorkerHost): Promise<void> {
  try {
    while (true) {
      if (host.pausedForNetwork() || !host.sessionService.isOnline()) break;

      const next = await host.mutex.run(() => pickNextQueueRow(LocalDatabase.getAll()));
      if (!next) break;

      const key = queueKey(next.bookId, next.trackId);
      if (host.userCancelledKeys.delete(key)) {
        host.failureCounts.delete(key);
        continue;
      }

      if (LocalDatabase.resolveLocalTrackPath(next.bookId, next.trackId, host.downloadsRoot)) {
        await host.mutex.run(async () => {
          host.failureCounts.delete(key);
          LocalDatabase.delete(next.bookId, next.trackId);
          if (next.batchId != null && next.batchId === host.getBulk().bulkBatchId) {
            host.addCompletedHistory(next);
          }
          host.completeAwaiter(key, "COMPLETED");
          host.refreshNotifierFromDb();
        });
        continue;
      }

      await host.mutex.run(async () => {
        host.patchState({
          activeBookId: next.bookId,
          activeTrackId: next.trackId,
          trackProgress: 0,
          pausedForNetwork: false,
        });
        host.broadcastState();
      });

      // downloadOne must NOT run under mutex
      const result = await downloadOne(host, next, key);

      let effectiveResult: DownloadAwaitResult = result;
      let completeAwaiter = true;
      await host.mutex.run(async () => {
        if (result === "COMPLETED") {
          host.failureCounts.delete(key);
          LocalDatabase.delete(next.bookId, next.trackId);
          host.addCompletedHistory(next);
        } else if (result === "CANCELLED") {
          host.failureCounts.delete(key);
          LocalDatabase.delete(next.bookId, next.trackId);
        } else if (
          LocalDatabase.resolveLocalTrackPath(next.bookId, next.trackId, host.downloadsRoot)
        ) {
          host.failureCounts.delete(key);
          LocalDatabase.delete(next.bookId, next.trackId);
          if (next.batchId != null && next.batchId === host.getBulk().bulkBatchId) {
            host.addCompletedHistory(next);
          }
          effectiveResult = "COMPLETED";
        } else if (result === "OFFLINE") {
          await persistPartProgress(host.downloadsRoot, next.bookId, next.trackId);
        } else {
          await persistPartProgress(host.downloadsRoot, next.bookId, next.trackId);
          const attempts = (host.failureCounts.get(key) ?? 0) + 1;
          if (attempts >= MAX_DOWNLOAD_FAILURES) {
            host.failureCounts.delete(key);
            reportDownloadFailure(
              host.mainWindow(),
              host.logDownloadFailure,
              next,
              result,
            );
            LocalDatabase.delete(next.bookId, next.trackId);
          } else {
            host.failureCounts.set(key, attempts);
            completeAwaiter = false;
          }
        }
        if (completeAwaiter) {
          host.completeAwaiter(key, effectiveResult);
        }
        host.refreshNotifierFromDb();
      });

      if (effectiveResult === "OFFLINE") break;
      await delay(50);
    }
  } finally {
    await host.mutex.run(async () => {
      host.setWorkerRunning(false);
      host.stopWorkerIfIdle();
    });
  }
}

/** Network IO — must not be called while holding the queue mutex. */
export async function downloadOne(
  host: TrackDownloadWorkerHost,
  entity: DownloadQueueRow,
  key: string,
): Promise<DownloadAwaitResult> {
  if (host.userCancelledKeys.has(key)) return "CANCELLED";

  try {
    await host.sessionService.refreshIfNeeded();
    if (!host.sessionService.getAccessToken()) return "FAILED";

    let lastNotifyBucket = -1;
    const partPath = resolveTrackPartPath(host.downloadsRoot, entity.bookId, entity.trackId);
    const offset =
      partPath && fs.existsSync(partPath) ? fs.statSync(partPath).size : entity.bytesDownloaded;

    const outcome = await host.downloadManager.downloadTrackResumable(
      entity.bookId,
      entity.trackId,
      offset,
      entity.totalBytes,
      (progress) => {
        const bucket = Math.floor(progress * 50);
        if (bucket > lastNotifyBucket || progress >= 1) {
          lastNotifyBucket = bucket;
          host.patchState({
            activeBookId: entity.bookId,
            activeTrackId: entity.trackId,
            trackProgress: progress,
          });
          host.broadcastState();
        }
      },
      () =>
        host.userCancelledKeys.has(key) ||
        host.pausedForNetwork() ||
        !host.sessionService.isOnline(),
    );

    let marked = LocalDatabase.markTrackDownloaded(
      entity.bookId,
      entity.trackId,
      outcome.finalPath,
      host.downloadsRoot,
    );
    if (!marked) {
      LocalDatabase.reconcileLocalDownloadPaths(host.downloadsRoot);
      marked = LocalDatabase.markTrackDownloaded(
        entity.bookId,
        entity.trackId,
        outcome.finalPath,
        host.downloadsRoot,
      );
      if (
        !marked &&
        !LocalDatabase.resolveLocalTrackPath(entity.bookId, entity.trackId, host.downloadsRoot)
      ) {
        if (!fs.existsSync(outcome.finalPath) || fs.statSync(outcome.finalPath).size <= 0) {
          return "FAILED";
        }
      }
    }
    notifyCatalogUpdated(host.mainWindow());
    return "COMPLETED";
  } catch (error) {
    if (error instanceof DownloadCancelledError || host.userCancelledKeys.has(key)) {
      return "CANCELLED";
    }
    if (!host.sessionService.isOnline() || host.pausedForNetwork()) {
      return "OFFLINE";
    }
    return "FAILED";
  }
}
