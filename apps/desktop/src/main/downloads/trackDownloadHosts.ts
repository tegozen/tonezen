import type { BrowserWindow } from "electron";
import type { DownloadAwaitResult, DownloadQueueState } from "@core/downloads/downloadQueueState.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import type { DownloadManager } from "./downloadManager.js";
import type { SessionService } from "../session/sessionService.js";
import type { DiagnosticErrorEntry } from "../app/diagnosticsLog.js";
import type { AsyncMutex } from "./asyncMutex.js";
import type { BulkCounters } from "./downloadQueueStateBuilder.js";
import type { TrackDownloadEnqueueHost } from "./trackDownloadEnqueue.js";
import {
  addCompletedHistory,
  patchState,
  refreshNotifierFromDb,
  startWorkerLocked,
  stopWorkerIfIdle,
  type TrackDownloadLifecycleHost,
} from "./trackDownloadLifecycle.js";
import { broadcastQueueState } from "./trackDownloadNotify.js";
import { runTrackDownloadWorker, type TrackDownloadWorkerHost } from "./trackDownloadWorker.js";

type DownloadFailureLogger = (entry: DiagnosticErrorEntry) => void | Promise<unknown>;

export interface TrackDownloadQueueHostsInput {
  mutex: AsyncMutex;
  downloadsRoot: string;
  downloadManager: DownloadManager;
  sessionService: SessionService;
  logDownloadFailure?: DownloadFailureLogger;
  getMainWindow: () => BrowserWindow | null;
  getState: () => DownloadQueueState;
  setState: (state: DownloadQueueState) => void;
  getBulk: () => BulkCounters;
  setBulk: (bulk: BulkCounters) => void;
  getPausedForNetwork: () => boolean;
  setPausedForNetwork: (paused: boolean) => void;
  getWorkerRunning: () => boolean;
  setWorkerRunning: (running: boolean) => void;
  userCancelledKeys: Set<string>;
  failureCounts: Map<string, number>;
  completeAwaiter: (key: string, result: DownloadAwaitResult) => void;
}

export function createTrackDownloadHosts(input: TrackDownloadQueueHostsInput) {
  const lifecycleHost = (): TrackDownloadLifecycleHost => ({
    downloadsRoot: input.downloadsRoot,
    downloadManager: input.downloadManager,
    sessionService: input.sessionService,
    getState: input.getState,
    setState: input.setState,
    getBulk: input.getBulk,
    setBulk: input.setBulk,
    getPausedForNetwork: input.getPausedForNetwork,
    setPausedForNetwork: input.setPausedForNetwork,
    getWorkerRunning: input.getWorkerRunning,
    setWorkerRunning: input.setWorkerRunning,
    broadcastState: () => broadcastQueueState(input.getMainWindow(), input.getState()),
    startWorker: () =>
      startWorkerLocked(lifecycleHost(), () => {
        void runTrackDownloadWorker(workerHost());
      }),
  });

  const enqueueHost = (): TrackDownloadEnqueueHost => ({
    downloadsRoot: input.downloadsRoot,
    downloadManager: input.downloadManager,
    failureCounts: input.failureCounts,
    userCancelledKeys: input.userCancelledKeys,
    completeAwaiter: input.completeAwaiter,
    refreshNotifierFromDb: () => refreshNotifierFromDb(lifecycleHost()),
    startWorkerLocked: () =>
      startWorkerLocked(lifecycleHost(), () => {
        void runTrackDownloadWorker(workerHost());
      }),
  });

  const workerHost = (): TrackDownloadWorkerHost => ({
    mutex: input.mutex,
    downloadsRoot: input.downloadsRoot,
    downloadManager: input.downloadManager,
    sessionService: input.sessionService,
    logDownloadFailure: input.logDownloadFailure,
    mainWindow: input.getMainWindow,
    pausedForNetwork: input.getPausedForNetwork,
    getBulk: input.getBulk,
    userCancelledKeys: input.userCancelledKeys,
    failureCounts: input.failureCounts,
    patchState: (patch) => patchState(lifecycleHost(), patch),
    broadcastState: () => broadcastQueueState(input.getMainWindow(), input.getState()),
    refreshNotifierFromDb: () => refreshNotifierFromDb(lifecycleHost()),
    addCompletedHistory: (entity: DownloadQueueRow) => addCompletedHistory(lifecycleHost(), entity),
    completeAwaiter: input.completeAwaiter,
    stopWorkerIfIdle: () => stopWorkerIfIdle(lifecycleHost()),
    setWorkerRunning: input.setWorkerRunning,
  });

  return { lifecycleHost, enqueueHost, workerHost };
}
