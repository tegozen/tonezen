import { alignedClientRevision, canAutoFlushProgress } from "@core/progress/progressMerge.js";
import type { AudiobookProgress } from "@core/types.js";
import { apiV1Url } from "@core/platform/serverPaths.js";
import { LocalDatabase } from "../db/localDatabase.js";
import type { ProgressPushResponse, ProgressRow, ProgressSyncConfig } from "./progressSyncTypes.js";

export interface ProgressPushDeps {
  config: ProgressSyncConfig;
  getAccessToken: () => string | null;
  refreshSession: () => Promise<unknown>;
}

function repairStuckRevision(
  progress: AudiobookProgress & { pendingSync?: boolean },
): AudiobookProgress & { pendingSync?: boolean } {
  const aligned = alignedClientRevision(progress.revision, progress.serverRevision);
  if (aligned === progress.revision) return progress;
  const pending = progress.pendingSync !== false;
  const repaired = { ...progress, revision: aligned };
  LocalDatabase.upsertProgress(repaired, pending, {
    conflictChoiceKey: progress.conflictChoiceKey ?? null,
  });
  return { ...repaired, pendingSync: pending };
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

  const storedRaw = LocalDatabase.getProgress(progress.bookId) ?? progress;
  const stored = repairStuckRevision(storedRaw);
  if (!canAutoFlushProgress(stored)) {
    return;
  }

  const baseRevision = stored.serverRevision ?? stored.revision ?? 0;

  const res = await fetch(apiV1Url(deps.config.baseUrl, `/progress/audiobooks/${progress.bookId}`), {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      track_id: stored.trackId,
      position_ms: stored.positionMs,
      base_revision: baseRevision,
    }),
  });

  if (res.status === 409) {
    const data = (await res.json().catch(() => null)) as ProgressPushResponse | null;
    if (data?.progress) applyRemote(data.progress);
    // Snapshot refreshed — retry once if local is still auto-flushable (e.g. local ahead).
    const latestRaw = LocalDatabase.getProgress(progress.bookId);
    const latest = latestRaw ? repairStuckRevision(latestRaw) : null;
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
    const repaired = repairStuckRevision(progress);
    if (!canAutoFlushProgress(repaired)) continue;
    await pushProgress(deps, repaired, applyRemote, applyPushAccepted);
  }
}
