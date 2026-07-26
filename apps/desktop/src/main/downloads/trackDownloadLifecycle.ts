import { computeBulkDownloaded } from "@core/downloads/downloadQueuePolicy.js";
import type { DownloadQueueState } from "@core/downloads/downloadQueueState.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import { LocalDatabase } from "../db/localDatabase.js";
import {
  appendCompletedHistory,
  buildNotifierState,
  type BulkCounters,
} from "./downloadQueueStateBuilder.js";
import { persistPartProgress } from "./trackDownloadEnqueue.js";
import type { DownloadManager } from "./downloadManager.js";
import type { SessionService } from "../session/sessionService.js";

export interface TrackDownloadLifecycleHost {
  downloadsRoot: string;
  downloadManager: DownloadManager;
  sessionService: SessionService;
  getState: () => DownloadQueueState;
  setState: (state: DownloadQueueState) => void;
  getBulk: () => BulkCounters;
  setBulk: (bulk: BulkCounters) => void;
  getPausedForNetwork: () => boolean;
  setPausedForNetwork: (paused: boolean) => void;
  getWorkerRunning: () => boolean;
  setWorkerRunning: (running: boolean) => void;
  broadcastState: () => void;
  startWorker: () => void;
}

export async function pauseForNetworkLocked(host: TrackDownloadLifecycleHost): Promise<void> {
  if (host.getPausedForNetwork()) return;
  host.setPausedForNetwork(true);
  host.downloadManager.cancelActiveDownload();
  const state = host.getState();
  if (state.activeBookId && state.activeTrackId) {
    await persistPartProgress(host.downloadsRoot, state.activeBookId, state.activeTrackId);
  }
  refreshNotifierFromDb(host, true);
}

export async function resumeWhenOnlineLocked(host: TrackDownloadLifecycleHost): Promise<void> {
  if (!host.sessionService.isOnline()) return;
  host.setPausedForNetwork(false);
  refreshNotifierFromDb(host, false);
  host.startWorker();
}

export function refreshNotifierFromDb(
  host: TrackDownloadLifecycleHost,
  paused: boolean = host.getPausedForNetwork(),
): void {
  const built = buildNotifierState({
    rows: LocalDatabase.getAll(),
    previous: host.getState(),
    paused,
    bulk: host.getBulk(),
  });
  host.setState(built.state);
  host.setBulk(built.bulk);
  host.broadcastState();
}

export function addCompletedHistory(
  host: TrackDownloadLifecycleHost,
  entity: DownloadQueueRow,
): void {
  host.setState(appendCompletedHistory(host.getState(), entity, host.getBulk().bulkBatchId));
}

export function patchState(
  host: TrackDownloadLifecycleHost,
  patch: Partial<DownloadQueueState>,
): void {
  host.setState({ ...host.getState(), ...patch });
}

export function stopWorkerIfIdle(host: TrackDownloadLifecycleHost): void {
  if (LocalDatabase.getAll().length > 0) return;
  const bulk = host.getBulk();
  const bulkDone = computeBulkDownloaded(
    bulk.bulkSkipped,
    bulk.bulkBatchId,
    host.getState().completedHistory,
  );
  if (bulk.bulkBatchId != null && bulk.bulkTotal > 0 && bulkDone >= bulk.bulkTotal) {
    host.setBulk({ bulkBatchId: null, bulkTotal: 0, bulkSkipped: 0 });
  }
  host.setWorkerRunning(false);
  patchState(host, {
    activeBookId: null,
    activeTrackId: null,
    trackProgress: null,
  });
}

export function startWorkerLocked(
  host: TrackDownloadLifecycleHost,
  runWorker: () => void,
): void {
  if (host.getWorkerRunning()) return;
  if (host.getPausedForNetwork() || !host.sessionService.isOnline()) return;
  host.setWorkerRunning(true);
  runWorker();
}
