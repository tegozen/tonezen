import { COMPLETED_HISTORY_LIMIT } from "@core/downloads/downloadResumePolicy.js";
import {
  bulkProgressFraction,
  emptyMusicDownloadState,
  isBulkDownloading,
  isMusicDownloadActive,
  isTrackDownloading,
  progressForTrack,
  type MusicDownloadState,
} from "@core/downloads/musicDownloadState.js";
import type { DownloadPriority } from "@core/downloads/downloadQueuePolicy.js";

export type DownloadQueueItemStatus =
  | "QUEUED"
  | "DOWNLOADING"
  | "COMPLETED"
  | "CANCELLED"
  | "FAILED"
  | "PAUSED_OFFLINE";

export type DownloadAwaitResult = "COMPLETED" | "CANCELLED" | "FAILED" | "OFFLINE";

export interface EnqueueDownloadRequest {
  bookId: string;
  trackId: string;
  priority: DownloadPriority;
  batchId?: string | null;
  title: string;
  subtitle?: string | null;
  contentType: string;
  enqueuedAt?: number;
}

export interface DownloadQueueItem {
  bookId: string;
  trackId: string;
  title: string;
  subtitle: string | null;
  contentType: string;
  status: DownloadQueueItemStatus;
  progress: number | null;
  batchId: string | null;
  enqueuedAt: number;
  completedAt: number | null;
}

export interface DownloadQueueState extends MusicDownloadState {
  queuedItems: DownloadQueueItem[];
  completedHistory: DownloadQueueItem[];
  activeBookId: string | null;
  activeBatchId: string | null;
  pausedForNetwork: boolean;
}

export const emptyDownloadQueueState = (): DownloadQueueState => ({
  ...emptyMusicDownloadState(),
  queuedItems: [],
  completedHistory: [],
  activeBookId: null,
  activeBatchId: null,
  pausedForNetwork: false,
});

export function isDownloadQueueActive(state: DownloadQueueState): boolean {
  return (
    state.queuedItems.some(
      (item) =>
        item.status === "QUEUED" ||
        item.status === "DOWNLOADING" ||
        item.status === "PAUSED_OFFLINE",
    ) || state.activeTrackId != null
  );
}

export function isTrackQueued(state: DownloadQueueState, trackId: string): boolean {
  return state.queuedItems.some(
    (item) =>
      item.trackId === trackId &&
      (item.status === "QUEUED" || item.status === "PAUSED_OFFLINE"),
  );
}

export function trimCompletedHistory(state: DownloadQueueState): DownloadQueueState {
  if (state.completedHistory.length <= COMPLETED_HISTORY_LIMIT) return state;
  return {
    ...state,
    completedHistory: state.completedHistory.slice(-COMPLETED_HISTORY_LIMIT),
  };
}

export {
  bulkProgressFraction,
  isBulkDownloading,
  isMusicDownloadActive,
  isTrackDownloading,
  progressForTrack,
};
