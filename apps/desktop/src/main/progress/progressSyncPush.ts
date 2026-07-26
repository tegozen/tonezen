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
): Promise<void> {
  await deps.refreshSession();
  const token = deps.getAccessToken();
  if (!token) return;

  const res = await fetch(apiV1Url(deps.config.baseUrl, `/progress/audiobooks/${progress.bookId}`), {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      track_id: progress.trackId,
      position_ms: progress.positionMs,
      updated_at: progress.updatedAt,
    }),
  });
  if (!res.ok) return;

  const data = (await res.json().catch(() => null)) as ProgressPushResponse | null;
  if (data?.progress) {
    applyRemote(data.progress);
    return;
  }
  LocalDatabase.markProgressSynced(progress.bookId);
}

export async function flushPendingProgress(
  deps: ProgressPushDeps,
  applyRemote: (row: ProgressRow) => void,
): Promise<void> {
  for (const progress of LocalDatabase.getPendingProgress()) {
    await pushProgress(deps, progress, applyRemote);
  }
}
