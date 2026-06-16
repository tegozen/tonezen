export type DownloadPriority = "PREFETCH" | "BULK" | "USER" | "PLAY";

const PRIORITY_WEIGHT: Record<DownloadPriority, number> = {
  PREFETCH: 1,
  BULK: 2,
  USER: 3,
  PLAY: 4,
};

export interface DownloadQueueKey {
  bookId: string;
  trackId: string;
}

export interface DownloadQueueSortable {
  key: DownloadQueueKey;
  priority: DownloadPriority;
  enqueuedAt: number;
}

export function sortPending(items: DownloadQueueSortable[]): DownloadQueueSortable[] {
  return [...items].sort((a, b) => {
    const byPriority = PRIORITY_WEIGHT[b.priority] - PRIORITY_WEIGHT[a.priority];
    if (byPriority !== 0) return byPriority;
    return a.enqueuedAt - b.enqueuedAt;
  });
}

export function mergePriority(current: DownloadPriority, incoming: DownloadPriority): DownloadPriority {
  return PRIORITY_WEIGHT[incoming] > PRIORITY_WEIGHT[current] ? incoming : current;
}

export function shouldUpgrade(current: DownloadPriority, incoming: DownloadPriority): boolean {
  return PRIORITY_WEIGHT[incoming] > PRIORITY_WEIGHT[current];
}

export function computeBulkDownloaded(
  bulkSkipped: number,
  bulkBatchId: string | null,
  completedHistory: ReadonlyArray<{ batchId?: string | null }>,
): number {
  if (!bulkBatchId) return 0;
  return (
    bulkSkipped +
    completedHistory.filter((item) => item.batchId === bulkBatchId).length
  );
}

export function isBulkBatchComplete(
  bulkSkipped: number,
  bulkTotal: number,
  bulkBatchId: string | null,
  completedHistory: ReadonlyArray<{ batchId?: string | null }>,
): boolean {
  if (!bulkBatchId || bulkTotal <= 0) return false;
  return computeBulkDownloaded(bulkSkipped, bulkBatchId, completedHistory) >= bulkTotal;
}
