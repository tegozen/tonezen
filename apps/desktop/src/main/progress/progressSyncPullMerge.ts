import type { BrowserWindow } from "electron";
import {
  getServerSnapshot,
  hasProgressSyncConflict,
} from "@core/progress/progressMerge.js";
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

function rowToRemote(row: ProgressRow): AudiobookProgress {
  const revision = Number(row.revision);
  return {
    bookId: row.book_id,
    trackId: row.track_id,
    positionMs: row.position_ms,
    updatedAt: row.updated_at,
    revision: Number.isFinite(revision) ? revision : 0,
    serverTrackId: row.track_id,
    serverPositionMs: row.position_ms,
    serverRevision: Number.isFinite(revision) ? revision : 0,
  };
}

export function applyRemoteProgress(
  row: ProgressRow,
  mainWindow: BrowserWindow | null,
  options?: { preferRemote?: boolean },
): void {
  const remote = rowToRemote(row);
  const preferRemote = options?.preferRemote === true;
  const local = LocalDatabase.getProgress(remote.bookId);

  if (preferRemote || !local) {
    const applied = LocalDatabase.applyServerToPlayHead(
      remote.bookId,
      {
        trackId: remote.trackId,
        positionMs: remote.positionMs,
        revision: remote.revision,
        updatedAt: remote.updatedAt,
      },
      null,
    );
    mainWindow?.webContents.send("progress:updated", applied);
    return;
  }

  // Always refresh server snapshot; keep play head when pending and divergent.
  const prevSnapshot = getServerSnapshot(local);
  const snapshotChanged =
    !prevSnapshot ||
    prevSnapshot.trackId !== remote.trackId ||
    prevSnapshot.positionMs !== remote.positionMs ||
    prevSnapshot.revision !== remote.revision;

  const next: AudiobookProgress = {
    ...local,
    serverTrackId: remote.trackId,
    serverPositionMs: remote.positionMs,
    serverRevision: remote.revision,
    // CAS base follows server when play head is not dirty
    revision: local.pendingSync ? local.revision : remote.revision,
  };

  if (local.pendingSync && hasProgressSyncConflict(local, getServerSnapshot(next))) {
    LocalDatabase.upsertProgress(next, true, {
      conflictChoiceKey: snapshotChanged ? null : local.conflictChoiceKey,
    });
    mainWindow?.webContents.send("progress:updated", { ...next, pendingSync: true });
    return;
  }

  if (!local.pendingSync) {
    const applied = LocalDatabase.applyServerToPlayHead(
      remote.bookId,
      {
        trackId: remote.trackId,
        positionMs: remote.positionMs,
        revision: remote.revision,
        updatedAt: remote.updatedAt,
      },
      snapshotChanged ? null : local.conflictChoiceKey,
    );
    mainWindow?.webContents.send("progress:updated", applied);
    return;
  }

  LocalDatabase.upsertProgress(next, true, {
    conflictChoiceKey: snapshotChanged ? null : local.conflictChoiceKey,
  });
  mainWindow?.webContents.send("progress:updated", { ...next, pendingSync: true });
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

  const data = (await res.json()) as { progress?: ProgressRow[] };

  for (const row of data.progress ?? []) {
    applyRemoteProgress(row, deps.mainWindow, { preferRemote: options?.preferRemote });
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
