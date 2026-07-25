import { randomUUID } from "node:crypto";
import fs from "node:fs";
import type { BrowserWindow } from "electron";
import {
  computeBulkDownloaded,
  mergePriority,
  sortPending,
  type DownloadPriority,
} from "@core/downloads/downloadQueuePolicy.js";
import type { DownloadQueueRow } from "./db/downloadQueueDb.js";
import {
  emptyDownloadQueueState,
  trimCompletedHistory,
  type DownloadAwaitResult,
  type DownloadQueueItem,
  type DownloadQueueItemStatus,
  type DownloadQueueState,
  type EnqueueDownloadRequest,
} from "@core/downloads/downloadQueueState.js";
import { isSafeStorageId, resolveTrackPartPath } from "@core/platform/safeLocalPaths.js";
import { DownloadCancelledError, type DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "./db/localDatabase.js";
import type { SessionService } from "./sessionService.js";
import type { DiagnosticErrorEntry } from "./diagnosticsLog.js";

const STATUS_QUEUED = "queued";
const MAX_DOWNLOAD_FAILURES = 3;

class AsyncMutex {
  private chain: Promise<void> = Promise.resolve();

  run<T>(fn: () => Promise<T> | T): Promise<T> {
    const next = this.chain.then(fn, fn);
    this.chain = next.then(
      () => undefined,
      () => undefined,
    );
    return next;
  }
}

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
  private bulkBatchId: string | null = null;
  private bulkTotal = 0;
  private bulkSkipped = 0;
  private mainWindow: BrowserWindow | null = null;

  constructor(
    private readonly downloadsRoot: string,
    private readonly downloadManager: DownloadManager,
    private readonly sessionService: SessionService,
    private readonly logDownloadFailure?: DownloadFailureLogger,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  setOnline(online: boolean): void {
    if (online) {
      void this.mutex.run(async () => {
        this.pausedForNetwork = false;
        await this.resumeWhenOnlineLocked();
      });
      return;
    }
    void this.mutex.run(async () => {
      await this.pauseForNetworkLocked();
    });
  }

  getQueueState(): DownloadQueueState {
    return this.state;
  }

  enqueue(request: EnqueueDownloadRequest): void {
    void this.mutex.run(async () => {
      await this.enqueueLocked(request);
    });
  }

  enqueueBatch(requests: EnqueueDownloadRequest[], batchId: string = randomUUID()): void {
    if (requests.length === 0) return;
    void this.mutex.run(async () => {
      this.bulkBatchId = batchId;
      this.bulkTotal = requests.length;
      let skipped = 0;
      for (const request of requests) {
        const req = { ...request, batchId };
        if (!isSafeStorageId(req.bookId) || !isSafeStorageId(req.trackId)) continue;
        if (LocalDatabase.resolveLocalTrackPath(req.bookId, req.trackId, this.downloadsRoot)) {
          skipped++;
          this.completeAwaiter(queueKey(req.bookId, req.trackId), "COMPLETED");
        } else {
          await this.enqueueLocked(req, false);
        }
      }
      this.bulkSkipped = skipped;
      this.refreshNotifierFromDb();
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
        await this.enqueueLocked({
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
      this.refreshNotifierFromDb();
      this.stopWorkerIfIdle();
    });
  }

  cancelBatch(batchId: string): void {
    void this.mutex.run(async () => {
      const items = LocalDatabase.getAll().filter((item) => item.batchId === batchId);
      for (const item of items) {
        await this.cancelTrackLocked(item.bookId, item.trackId);
      }
      if (this.bulkBatchId === batchId) {
        this.bulkBatchId = null;
        this.bulkTotal = 0;
        this.bulkSkipped = 0;
      }
      this.refreshNotifierFromDb();
      this.stopWorkerIfIdle();
    });
  }

  cancelAll(): Promise<void> {
    return this.mutex.run(async () => {
      this.downloadManager.cancelActiveDownload();
      const items = LocalDatabase.getAll();
      for (const item of items) {
        await this.cancelTrackLocked(item.bookId, item.trackId);
      }
      this.failureCounts.clear();
      LocalDatabase.deleteAll();
      this.bulkBatchId = null;
      this.bulkTotal = 0;
      this.bulkSkipped = 0;
      this.refreshNotifierFromDb();
      this.stopWorkerIfIdle();
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
      this.refreshNotifierFromDb();
      if (this.sessionService.isOnline()) {
        this.startWorkerLocked();
      }
    });
  }

  private async pauseForNetworkLocked(): Promise<void> {
    if (this.pausedForNetwork) return;
    this.pausedForNetwork = true;
    this.downloadManager.cancelActiveDownload();
    await this.persistActivePartProgress();
    this.refreshNotifierFromDb(true);
  }

  private async resumeWhenOnlineLocked(): Promise<void> {
    if (!this.sessionService.isOnline()) return;
    this.pausedForNetwork = false;
    this.refreshNotifierFromDb(false);
    this.startWorkerLocked();
  }

  private async enqueueLocked(
    request: EnqueueDownloadRequest,
    refreshNotifier = true,
  ): Promise<void> {
    if (!isSafeStorageId(request.bookId) || !isSafeStorageId(request.trackId)) return;
    const key = queueKey(request.bookId, request.trackId);
    if (LocalDatabase.resolveLocalTrackPath(request.bookId, request.trackId, this.downloadsRoot)) {
      this.failureCounts.delete(key);
      this.completeAwaiter(key, "COMPLETED");
      return;
    }

    const existing = LocalDatabase.get(request.bookId, request.trackId);
    const priority = existing
      ? mergePriority(existing.priority as DownloadPriority, request.priority)
      : request.priority;
    const partPath = resolveTrackPartPath(this.downloadsRoot, request.bookId, request.trackId);
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
      this.refreshNotifierFromDb();
    }
    this.startWorkerLocked();
  }

  private startWorkerLocked(): void {
    if (this.workerRunning) return;
    if (this.pausedForNetwork || !this.sessionService.isOnline()) return;
    this.workerRunning = true;
    void this.runWorker();
  }

  private async runWorker(): Promise<void> {
    try {
      while (true) {
        if (this.pausedForNetwork || !this.sessionService.isOnline()) break;

        const next = await this.mutex.run(() => this.pickNextLocked());
        if (!next) break;

        const key = queueKey(next.bookId, next.trackId);
        if (this.userCancelledKeys.delete(key)) {
          this.failureCounts.delete(key);
          continue;
        }

        if (LocalDatabase.resolveLocalTrackPath(next.bookId, next.trackId, this.downloadsRoot)) {
          await this.mutex.run(async () => {
            this.failureCounts.delete(key);
            LocalDatabase.delete(next.bookId, next.trackId);
            if (next.batchId != null && next.batchId === this.bulkBatchId) {
              this.addCompletedHistory(next);
            }
            this.completeAwaiter(key, "COMPLETED");
            this.refreshNotifierFromDb();
          });
          continue;
        }

        await this.mutex.run(async () => {
          this.patchState({
            activeBookId: next.bookId,
            activeTrackId: next.trackId,
            trackProgress: 0,
            pausedForNetwork: false,
          });
          this.broadcastState();
        });

        const result = await this.downloadOne(next, key);

        let effectiveResult: DownloadAwaitResult = result;
        let completeAwaiter = true;
        await this.mutex.run(async () => {
          if (result === "COMPLETED") {
            this.failureCounts.delete(key);
            LocalDatabase.delete(next.bookId, next.trackId);
            this.addCompletedHistory(next);
          } else if (result === "CANCELLED") {
            this.failureCounts.delete(key);
            LocalDatabase.delete(next.bookId, next.trackId);
          } else {
            if (LocalDatabase.resolveLocalTrackPath(next.bookId, next.trackId, this.downloadsRoot)) {
              this.failureCounts.delete(key);
              LocalDatabase.delete(next.bookId, next.trackId);
              if (next.batchId != null && next.batchId === this.bulkBatchId) {
                this.addCompletedHistory(next);
              }
              effectiveResult = "COMPLETED";
            } else if (result === "OFFLINE") {
              await this.persistPartProgress(next.bookId, next.trackId);
            } else {
              await this.persistPartProgress(next.bookId, next.trackId);
              const attempts = (this.failureCounts.get(key) ?? 0) + 1;
              if (attempts >= MAX_DOWNLOAD_FAILURES) {
                this.failureCounts.delete(key);
                this.reportDownloadFailure(next, result);
                LocalDatabase.delete(next.bookId, next.trackId);
              } else {
                this.failureCounts.set(key, attempts);
                completeAwaiter = false;
              }
            }
          }
          if (completeAwaiter) {
            this.completeAwaiter(key, effectiveResult);
          }
          this.refreshNotifierFromDb();
        });

        if (effectiveResult === "OFFLINE") break;
        await delay(50);
      }
    } finally {
      await this.mutex.run(async () => {
        this.workerRunning = false;
        this.stopWorkerIfIdle();
      });
    }
  }

  private async downloadOne(
    entity: DownloadQueueRow,
    key: string,
  ): Promise<DownloadAwaitResult> {
    if (this.userCancelledKeys.has(key)) return "CANCELLED";

    try {
      await this.sessionService.refreshIfNeeded();
      if (!this.sessionService.getAccessToken()) return "FAILED";

      let lastNotifyBucket = -1;
      const partPath = resolveTrackPartPath(this.downloadsRoot, entity.bookId, entity.trackId);
      const offset =
        partPath && fs.existsSync(partPath) ? fs.statSync(partPath).size : entity.bytesDownloaded;

      const outcome = await this.downloadManager.downloadTrackResumable(
        entity.bookId,
        entity.trackId,
        offset,
        entity.totalBytes,
        (progress) => {
          const bucket = Math.floor(progress * 50);
          if (bucket > lastNotifyBucket || progress >= 1) {
            lastNotifyBucket = bucket;
            this.patchState({
              activeBookId: entity.bookId,
              activeTrackId: entity.trackId,
              trackProgress: progress,
            });
            this.broadcastState();
          }
        },
        () =>
          this.userCancelledKeys.has(key) ||
          this.pausedForNetwork ||
          !this.sessionService.isOnline(),
      );

      let marked = LocalDatabase.markTrackDownloaded(
        entity.bookId,
        entity.trackId,
        outcome.finalPath,
        this.downloadsRoot,
      );
      if (!marked) {
        LocalDatabase.reconcileLocalDownloadPaths(this.downloadsRoot);
        marked = LocalDatabase.markTrackDownloaded(
          entity.bookId,
          entity.trackId,
          outcome.finalPath,
          this.downloadsRoot,
        );
        if (
          !marked &&
          !LocalDatabase.resolveLocalTrackPath(entity.bookId, entity.trackId, this.downloadsRoot)
        ) {
          if (!fs.existsSync(outcome.finalPath) || fs.statSync(outcome.finalPath).size <= 0) {
            return "FAILED";
          }
        }
      }
      this.notifyCatalogUpdated();
      return "COMPLETED";
    } catch (error) {
      if (error instanceof DownloadCancelledError || this.userCancelledKeys.has(key)) {
        return "CANCELLED";
      }
      if (!this.sessionService.isOnline() || this.pausedForNetwork) {
        return "OFFLINE";
      }
      return "FAILED";
    }
  }

  private pickNextLocked(): DownloadQueueRow | null {
    const pending = LocalDatabase.getAll()
      .map((entity) => {
        try {
          return {
            sortable: {
              key: { bookId: entity.bookId, trackId: entity.trackId },
              priority: entity.priority as DownloadPriority,
              enqueuedAt: entity.enqueuedAt,
            },
            entity,
          };
        } catch {
          return null;
        }
      })
      .filter((item): item is NonNullable<typeof item> => item != null);

    const sorted = sortPending(pending.map((item) => item.sortable));
    const firstKey = sorted[0]?.key;
    if (!firstKey) return null;
    return pending.find(
      (item) =>
        item.entity.bookId === firstKey.bookId && item.entity.trackId === firstKey.trackId,
    )?.entity ?? null;
  }

  private async persistActivePartProgress(): Promise<void> {
    const activeBookId = this.state.activeBookId;
    const activeTrackId = this.state.activeTrackId;
    if (!activeBookId || !activeTrackId) return;
    await this.persistPartProgress(activeBookId, activeTrackId);
  }

  private async persistPartProgress(bookId: string, trackId: string): Promise<void> {
    const partPath = resolveTrackPartPath(this.downloadsRoot, bookId, trackId);
    if (!partPath) return;
    const length = fs.existsSync(partPath) ? fs.statSync(partPath).size : 0;
    LocalDatabase.updateProgress(bookId, trackId, length, null, partPath);
  }

  private completeAwaiter(key: string, result: DownloadAwaitResult): void {
    const awaiter = this.awaiters.get(key);
    if (!awaiter) return;
    this.awaiters.delete(key);
    awaiter.resolve(result);
  }

  private async cancelTrackLocked(bookId: string, trackId: string): Promise<void> {
    const key = queueKey(bookId, trackId);
    this.userCancelledKeys.add(key);
    this.failureCounts.delete(key);
    LocalDatabase.delete(bookId, trackId);
    await this.downloadManager.deleteLocalTrack(bookId, trackId);
    this.completeAwaiter(key, "CANCELLED");
  }

  private refreshNotifierFromDb(paused: boolean = this.pausedForNetwork): void {
    const rows = LocalDatabase.getAll();
    const activeBookId = this.state.activeBookId;
    const activeTrackId = this.state.activeTrackId;
    const activeProgress = this.state.trackProgress;
    const entityByKey = new Map(rows.map((entity) => [queueKey(entity.bookId, entity.trackId), entity]));

    const items: DownloadQueueItem[] = rows.map((entity) => {
      const isActive =
        !paused && entity.bookId === activeBookId && entity.trackId === activeTrackId;
      let status: DownloadQueueItemStatus;
      if (paused) {
        status = "PAUSED_OFFLINE";
      } else if (isActive) {
        status = "DOWNLOADING";
      } else {
        status = "QUEUED";
      }
      return {
        bookId: entity.bookId,
        trackId: entity.trackId,
        title: entity.title,
        subtitle: entity.subtitle,
        contentType: entity.contentType,
        status,
        progress: isActive ? activeProgress : null,
        batchId: entity.batchId,
        enqueuedAt: entity.enqueuedAt,
        completedAt: null,
      };
    });

    const itemsByKey = new Map(items.map((item) => [queueKey(item.bookId, item.trackId), item]));
    const sortables = items.map((item) => {
      const row = entityByKey.get(queueKey(item.bookId, item.trackId))!;
      return {
        key: { bookId: item.bookId, trackId: item.trackId },
        priority: row.priority as DownloadPriority,
        enqueuedAt: item.enqueuedAt,
      };
    });
    const sortedItems = sortPending(sortables)
      .map((sortable) => itemsByKey.get(queueKey(sortable.key.bookId, sortable.key.trackId)))
      .filter((item): item is DownloadQueueItem => item != null);

    const bulkBatch = this.bulkBatchId;
    const bulkDone = computeBulkDownloaded(
      this.bulkSkipped,
      bulkBatch,
      this.state.completedHistory,
    );
    this.maybeFinishBulkBatchLocked(bulkDone);
    const activeBatch = this.bulkBatchId;

    this.state = trimCompletedHistory({
      ...this.state,
      queuedItems: sortedItems,
      activeBookId: paused ? null : activeBookId,
      activeTrackId: paused ? null : activeTrackId,
      trackProgress: paused ? null : activeProgress,
      bulkTotal: activeBatch != null ? this.bulkTotal : 0,
      bulkDownloaded: activeBatch != null ? bulkDone : 0,
      activeBatchId: activeBatch,
      pausedForNetwork: paused,
    });
    this.broadcastState();
  }

  private maybeFinishBulkBatchLocked(bulkDone: number): void {
    if (this.bulkBatchId == null || this.bulkTotal <= 0 || bulkDone < this.bulkTotal) {
      return;
    }
    this.bulkBatchId = null;
    this.bulkTotal = 0;
    this.bulkSkipped = 0;
  }

  private addCompletedHistory(entity: DownloadQueueRow): void {
    const completed: DownloadQueueItem = {
      bookId: entity.bookId,
      trackId: entity.trackId,
      title: entity.title,
      subtitle: entity.subtitle,
      contentType: entity.contentType,
      status: "COMPLETED",
      progress: 1,
      batchId: entity.batchId,
      enqueuedAt: entity.enqueuedAt,
      completedAt: Date.now(),
    };
    this.state = trimCompletedHistory({
      ...this.state,
      completedHistory: [...this.state.completedHistory, completed],
      activeBookId: null,
      activeTrackId: null,
      trackProgress: null,
      bulkDownloaded:
        entity.batchId != null && entity.batchId === this.bulkBatchId
          ? this.state.bulkDownloaded + 1
          : this.state.bulkDownloaded,
    });
  }

  private patchState(patch: Partial<DownloadQueueState>): void {
    this.state = { ...this.state, ...patch };
  }

  private stopWorkerIfIdle(): void {
    if (LocalDatabase.getAll().length > 0) return;
    const bulkDone = computeBulkDownloaded(
      this.bulkSkipped,
      this.bulkBatchId,
      this.state.completedHistory,
    );
    this.maybeFinishBulkBatchLocked(bulkDone);
    this.workerRunning = false;
    this.patchState({
      activeBookId: null,
      activeTrackId: null,
      trackProgress: null,
    });
  }

  private broadcastState(): void {
    const window = this.mainWindow;
    if (!window || window.isDestroyed()) return;
    window.webContents.send("download:queueState", this.state);
  }

  private notifyCatalogUpdated(): void {
    const window = this.mainWindow;
    if (!window || window.isDestroyed()) return;
    window.webContents.send("catalog:updated");
  }

  private reportDownloadFailure(entity: DownloadQueueRow, code: DownloadAwaitResult): void {
    const entry: DiagnosticErrorEntry = {
      area: "download",
      message: "Не удалось скачать",
      code,
      bookId: entity.bookId,
      trackId: entity.trackId,
      bookTitle: entity.subtitle ?? undefined,
      trackTitle: entity.title,
    };
    try {
      void Promise.resolve(this.logDownloadFailure?.(entry)).catch(() => {});
    } catch {
      // Diagnostics must never block the download worker.
    }
    const window = this.mainWindow;
    if (!window || window.isDestroyed()) return;
    window.webContents.send("download:failed", entry);
  }
}

function queueKey(bookId: string, trackId: string): string {
  return `${bookId}\0${trackId}`;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
