import { canAutoFlushProgress } from "@core/progress/progressMerge.js";
import type { AudiobookProgress } from "@core/types.js";
import { apiV1Url } from "@core/platform/serverPaths.js";
import { LocalDatabase } from "../db/localDatabase.js";
import type { ProgressPushResponse, ProgressRow, ProgressSyncConfig } from "./progressSyncTypes.js";

export interface ProgressPushDeps {
  config: ProgressSyncConfig;
  getAccessToken: () => string | null;
  refreshSession: () => Promise<unknown>;
}

export async function pushProgress(
  deps: ProgressPushDeps,
  progress: AudiobookProgress,
  applyRemote: (row: ProgressRow) => void,
  applyPushAccepted: (row: ProgressRow) => void = applyRemote,
): Promise<void> {
  await deps.refreshSession();
  const token = deps.getAccessToken();
  if (!token) return;

  const stored = LocalDatabase.getProgress(progress.bookId);
  if (stored && !canAutoFlushProgress(stored)) {
    return;
  }

  const baseRevision =
    stored?.serverRevision ??
    stored?.revision ??
    progress.serverRevision ??
    progress.revision ??
    0;

  const res = await fetch(apiV1Url(deps.config.baseUrl, `/progress/audiobooks/${progress.bookId}`), {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      track_id: progress.trackId,
      position_ms: progress.positionMs,
      base_revision: baseRevision,
    }),
  });

  if (res.status === 409) {
    const data = (await res.json().catch(() => null)) as ProgressPushResponse | null;
    if (data?.progress) applyRemote(data.progress);
    // Snapshot refreshed — retry once if local is still auto-flushable (e.g. local ahead).
    const latest = LocalDatabase.getProgress(progress.bookId);
    if (latest?.pendingSync && canAutoFlushProgress(latest)) {
      const retryRes = await fetch(
        apiV1Url(deps.config.baseUrl, `/progress/audiobooks/${progress.bookId}`),
        {
          method: "PUT",
          headers: {
            Authorization: `Bearer ${token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            track_id: latest.trackId,
            position_ms: latest.positionMs,
            base_revision: latest.serverRevision ?? latest.revision ?? 0,
          }),
        },
      );
      if (retryRes.status === 409) {
        const retryData = (await retryRes.json().catch(() => null)) as ProgressPushResponse | null;
        if (retryData?.progress) applyRemote(retryData.progress);
        return;
      }
      if (!retryRes.ok) return;
      const retryData = (await retryRes.json().catch(() => null)) as ProgressPushResponse | null;
      if (retryData?.progress) {
        applyPushAccepted(retryData.progress);
        return;
      }
      LocalDatabase.markProgressSynced(
        progress.bookId,
        (latest.serverRevision ?? latest.revision ?? 0) + 1,
      );
    }
    return;
  }
  if (!res.ok) return;

  const data = (await res.json().catch(() => null)) as ProgressPushResponse | null;
  if (data?.progress) {
    // Server accepted our write — clear pending; do not use pull-merge (keeps pending).
    applyPushAccepted(data.progress);
    return;
  }
  LocalDatabase.markProgressSynced(progress.bookId, baseRevision === 0 ? 1 : baseRevision + 1);
}

export async function flushPendingProgress(
  deps: ProgressPushDeps,
  applyRemote: (row: ProgressRow) => void,
  applyPushAccepted: (row: ProgressRow) => void = applyRemote,
): Promise<void> {
  for (const progress of LocalDatabase.getPendingProgress()) {
    if (!canAutoFlushProgress(progress)) continue;
    await pushProgress(deps, progress, applyRemote, applyPushAccepted);
  }
}
