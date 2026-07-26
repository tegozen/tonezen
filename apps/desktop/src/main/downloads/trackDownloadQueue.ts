import { randomUUID } from "node:crypto";
import type { BrowserWindow } from "electron";
import type { DownloadPriority } from "@core/downloads/downloadQueuePolicy.js";
import {
  emptyDownloadQueueState,
  type DownloadAwaitResult,
  type DownloadQueueState,
  type EnqueueDownloadRequest,
} from "@core/downloads/downloadQueueState.js";
import { isSafeStorageId } from "@core/platform/safeLocalPaths.js";
import type { DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "../db/localDatabase.js";
import type { SessionService } from "../session/sessionService.js";
import type { DiagnosticErrorEntry } from "../app/diagnosticsLog.js";
import { AsyncMutex, queueKey } from "./asyncMutex.js";
import type { BulkCounters } from "./downloadQueueStateBuilder.js";
import { cancelTrackLocked, enqueueLocked } from "./trackDownloadEnqueue.js";
import { createTrackDownloadHosts } from "./trackDownloadHosts.js";
import {
  pauseForNetworkLocked,
  refreshNotifierFromDb,
  resumeWhenOnlineLocked,
  startWorkerLocked,
  stopWorkerIfIdle,
} from "./trackDownloadLifecycle.js";
import { runTrackDownloadWorker } from "./trackDownloadWorker.js";

type Awaiter = {
  resolve: (result: DownloadAwaitResult) => void;
};

type DownloadFailureLogger = (entry: DiagnosticErrorEntry) => void | Promise<unknown>;

export class TrackDownloadQueue {
  private readonly mutex = new AsyncMutex();
  private state: DownloadQueueState = emptyDownloadQueueState();
  private workerRunning = false;
  private pausedForNetwork = false;
  private readonly userCancelledKeys = new Set<string>();
  private readonly failureCounts = new Map<string, number>();
  private readonly awaiters = new Map<string, Awaiter>();
  private bulk: BulkCounters = { bulkBatchId: null, bulkTotal: 0, bulkSkipped: 0 };
  private mainWindow: BrowserWindow | null = null;
  private readonly hosts: ReturnType<typeof createTrackDownloadHosts>;

  constructor(
    private readonly downloadsRoot: string,
    private readonly downloadManager: DownloadManager,
    private readonly sessionService: SessionService,
    private readonly logDownloadFailure?: DownloadFailureLogger,
  ) {
    this.hosts = createTrackDownloadHosts({
      mutex: this.mutex,
      downloadsRoot: this.downloadsRoot,
      downloadManager: this.downloadManager,
      sessionService: this.sessionService,
      logDownloadFailure: this.logDownloadFailure,
      getMainWindow: () => this.mainWindow,
      getState: () => this.state,
      setState: (state) => {
        this.state = state;
      },
      getBulk: () => this.bulk,
      setBulk: (bulk) => {
        this.bulk = bulk;
      },
      getPausedForNetwork: () => this.pausedForNetwork,
      setPausedForNetwork: (paused) => {
        this.pausedForNetwork = paused;
      },
      getWorkerRunning: () => this.workerRunning,
      setWorkerRunning: (running) => {
        this.workerRunning = running;
      },
      userCancelledKeys: this.userCancelledKeys,
      failureCounts: this.failureCounts,
      completeAwaiter: (key, result) => this.completeAwaiter(key, result),
    });
  }

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  setOnline(online: boolean): void {
    if (online) {
      void this.mutex.run(async () => {
        this.pausedForNetwork = false;
        await resumeWhenOnlineLocked(this.hosts.lifecycleHost());
      });
      return;
    }
    void this.mutex.run(async () => {
      await pauseForNetworkLocked(this.hosts.lifecycleHost());
    });
  }

  getQueueState(): DownloadQueueState {
    return this.state;
  }

  enqueue(request: EnqueueDownloadRequest): void {
    void this.mutex.run(async () => {
      await enqueueLocked(this.hosts.enqueueHost(), request);
    });
  }

  enqueueBatch(requests: EnqueueDownloadRequest[], batchId: string = randomUUID()): void {
    if (requests.length === 0) return;
    void this.mutex.run(async () => {
      this.bulk = { bulkBatchId: batchId, bulkTotal: requests.length, bulkSkipped: 0 };
      let skipped = 0;
      for (const request of requests) {
        const req = { ...request, batchId };
        if (!isSafeStorageId(req.bookId) || !isSafeStorageId(req.trackId)) continue;
        if (LocalDatabase.resolveLocalTrackPath(req.bookId, req.trackId, this.downloadsRoot)) {
          skipped++;
          this.completeAwaiter(queueKey(req.bookId, req.trackId), "COMPLETED");
        } else {
          await enqueueLocked(this.hosts.enqueueHost(), req, false);
        }
      }
      this.bulk.bulkSkipped = skipped;
      refreshNotifierFromDb(this.hosts.lifecycleHost());
    });
  }

  async awaitTrack(
    bookId: string,
    trackId: string,
    options?: {
      priority?: DownloadPriority;
      title?: string;
      subtitle?: string | null;
      contentType?: string;
    },
  ): Promise<DownloadAwaitResult> {
    if (!isSafeStorageId(bookId) || !isSafeStorageId(trackId)) return "FAILED";
    const key = queueKey(bookId, trackId);
    if (LocalDatabase.resolveLocalTrackPath(bookId, trackId, this.downloadsRoot)) {
      return "COMPLETED";
    }

    return new Promise<DownloadAwaitResult>((resolve) => {
      this.awaiters.set(key, { resolve });
      void this.mutex.run(async () => {
        await enqueueLocked(this.hosts.enqueueHost(), {
          bookId,
          trackId,
          priority: options?.priority ?? "PLAY",
          title: options?.title ?? "",
          subtitle: options?.subtitle ?? null,
          contentType: options?.contentType ?? "music",
        });
      });
    });
  }

  cancelTrack(bookId: string, trackId: string): Promise<void> {
    if (!isSafeStorageId(bookId) || !isSafeStorageId(trackId)) return Promise.resolve();
    return this.mutex.run(async () => {
      const key = queueKey(bookId, trackId);
      this.userCancelledKeys.add(key);
      this.failureCounts.delete(key);
      LocalDatabase.delete(bookId, trackId);
      this.downloadManager.cancelActiveDownload();
      await this.downloadManager.deleteLocalTrack(bookId, trackId);
      this.completeAwaiter(key, "CANCELLED");
      refreshNotifierFromDb(this.hosts.lifecycleHost());
      stopWorkerIfIdle(this.hosts.lifecycleHost());
    });
  }

  cancelBatch(batchId: string): void {
    void this.mutex.run(async () => {
      const items = LocalDatabase.getAll().filter((item) => item.batchId === batchId);
      for (const item of items) {
        await cancelTrackLocked(this.hosts.enqueueHost(), item.bookId, item.trackId);
      }
      if (this.bulk.bulkBatchId === batchId) {
        this.bulk = { bulkBatchId: null, bulkTotal: 0, bulkSkipped: 0 };
      }
      refreshNotifierFromDb(this.hosts.lifecycleHost());
      stopWorkerIfIdle(this.hosts.lifecycleHost());
    });
  }

  cancelAll(): Promise<void> {
    return this.mutex.run(async () => {
      this.downloadManager.cancelActiveDownload();
      const items = LocalDatabase.getAll();
      for (const item of items) {
        await cancelTrackLocked(this.hosts.enqueueHost(), item.bookId, item.trackId);
      }
      this.failureCounts.clear();
      LocalDatabase.deleteAll();
      this.bulk = { bulkBatchId: null, bulkTotal: 0, bulkSkipped: 0 };
      refreshNotifierFromDb(this.hosts.lifecycleHost());
      stopWorkerIfIdle(this.hosts.lifecycleHost());
    });
  }

  async restoreFromDb(): Promise<void> {
    await this.mutex.run(async () => {
      const items = LocalDatabase.getAll();
      for (const entity of items) {
        if (LocalDatabase.resolveLocalTrackPath(entity.bookId, entity.trackId, this.downloadsRoot)) {
          LocalDatabase.delete(entity.bookId, entity.trackId);
        }
      }
      refreshNotifierFromDb(this.hosts.lifecycleHost());
      if (this.sessionService.isOnline()) {
        startWorkerLocked(this.hosts.lifecycleHost(), () => {
          void runTrackDownloadWorker(this.hosts.workerHost());
        });
      }
    });
  }

  private completeAwaiter(key: string, result: DownloadAwaitResult): void {
    const awaiter = this.awaiters.get(key);
    if (!awaiter) return;
    this.awaiters.delete(key);
    awaiter.resolve(result);
  }
}
