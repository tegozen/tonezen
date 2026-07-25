import {
  computeBulkDownloaded,
  sortPending,
  type DownloadPriority,
} from "@core/downloads/downloadQueuePolicy.js";
import {
  trimCompletedHistory,
  type DownloadQueueItem,
  type DownloadQueueItemStatus,
  type DownloadQueueState,
} from "@core/downloads/downloadQueueState.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import { queueKey } from "./asyncMutex.js";

export type BulkCounters = {
  bulkBatchId: string | null;
  bulkTotal: number;
  bulkSkipped: number;
};

export function buildNotifierState(input: {
  rows: DownloadQueueRow[];
  previous: DownloadQueueState;
  paused: boolean;
  bulk: BulkCounters;
}): { state: DownloadQueueState; bulk: BulkCounters } {
  const { rows, previous, paused } = input;
  let bulk = { ...input.bulk };

  const activeBookId = previous.activeBookId;
  const activeTrackId = previous.activeTrackId;
  const activeProgress = previous.trackProgress;
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

  const bulkDone = computeBulkDownloaded(
    bulk.bulkSkipped,
    bulk.bulkBatchId,
    previous.completedHistory,
  );
  if (bulk.bulkBatchId != null && bulk.bulkTotal > 0 && bulkDone >= bulk.bulkTotal) {
    bulk = { bulkBatchId: null, bulkTotal: 0, bulkSkipped: 0 };
  }
  const activeBatch = bulk.bulkBatchId;

  const state = trimCompletedHistory({
    ...previous,
    queuedItems: sortedItems,
    activeBookId: paused ? null : activeBookId,
    activeTrackId: paused ? null : activeTrackId,
    trackProgress: paused ? null : activeProgress,
    bulkTotal: activeBatch != null ? bulk.bulkTotal : 0,
    bulkDownloaded: activeBatch != null ? bulkDone : 0,
    activeBatchId: activeBatch,
    pausedForNetwork: paused,
  });

  return { state, bulk };
}

export function appendCompletedHistory(
  state: DownloadQueueState,
  entity: DownloadQueueRow,
  bulkBatchId: string | null,
): DownloadQueueState {
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
  return trimCompletedHistory({
    ...state,
    completedHistory: [...state.completedHistory, completed],
    activeBookId: null,
    activeTrackId: null,
    trackProgress: null,
    bulkDownloaded:
      entity.batchId != null && entity.batchId === bulkBatchId
        ? state.bulkDownloaded + 1
        : state.bulkDownloaded,
  });
}

export function pickNextQueueRow(rows: DownloadQueueRow[]): DownloadQueueRow | null {
  const pending = rows
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
  return (
    pending.find(
      (item) =>
        item.entity.bookId === firstKey.bookId && item.entity.trackId === firstKey.trackId,
    )?.entity ?? null
  );
}
