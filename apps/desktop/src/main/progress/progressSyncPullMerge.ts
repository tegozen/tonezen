import type { BrowserWindow } from "electron";
import { mergeProgressLww } from "@core/progress/progressMerge.js";
import type { AudiobookProgress } from "@core/types.js";
import { apiV1Url } from "@core/platform/serverPaths.js";
import { LocalDatabase } from "../db/localDatabase.js";
import type { ProgressRow, ProgressSyncConfig } from "./progressSyncTypes.js";

export interface ProgressPullMergeDeps {
  config: ProgressSyncConfig;
  getAccessToken: () => string | null;
  refreshSession: () => Promise<unknown>;
  mainWindow: BrowserWindow | null;
}

export function applyRemoteProgress(
  row: ProgressRow,
  mainWindow: BrowserWindow | null,
  options?: { preferRemote?: boolean },
): void {
  const remote: AudiobookProgress = {
    bookId: row.book_id,
    trackId: row.track_id,
    positionMs: row.position_ms,
    updatedAt: row.updated_at,
  };
  const preferRemote = options?.preferRemote === true;
  const local = LocalDatabase.getProgress(remote.bookId);
  if (!preferRemote) {
    const pendingLocal =
      local?.pendingSync &&
      local.updatedAt &&
      new Date(local.updatedAt) > new Date(remote.updatedAt);
    if (pendingLocal) return;
  }

  const merged = preferRemote ? remote : mergeProgressLww(local, remote);
  if (!merged) return;

  LocalDatabase.upsertProgress(merged, false);
  mainWindow?.webContents.send("progress:updated", merged);
}

export async function pullAllProgress(
  deps: ProgressPullMergeDeps,
  options?: { preferRemote?: boolean },
): Promise<boolean> {
  await deps.refreshSession();
  const token = deps.getAccessToken();
  if (!token) return false;

  const res = await fetch(apiV1Url(deps.config.baseUrl, "/progress/audiobooks"), {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) return false;

  const data = (await res.json()) as {
    progress?: Array<{
      book_id: string;
      track_id: string;
      position_ms: number;
      updated_at: string;
    }>;
  };

  for (const row of data.progress ?? []) {
    applyRemoteProgress(
      {
        book_id: row.book_id,
        track_id: row.track_id,
        position_ms: row.position_ms,
        updated_at: row.updated_at,
      },
      deps.mainWindow,
      { preferRemote: options?.preferRemote },
    );
  }
  return true;
}

export function recordLastSyncAt(): void {
  LocalDatabase.setLastSyncAtEpochMs(Date.now());
}

export function getProgressSyncStatus(): {
  pendingCount: number;
  lastSyncAtEpochMs: number | null;
} {
  return {
    pendingCount: LocalDatabase.getPendingSyncCount(),
    lastSyncAtEpochMs: LocalDatabase.getLastSyncAtEpochMs(),
  };
}

export async function triggerProgressSync(
  pullAll: () => Promise<void>,
  flushPending: () => Promise<void>,
): Promise<void> {
  await pullAll();
  await flushPending();
  recordLastSyncAt();
}
