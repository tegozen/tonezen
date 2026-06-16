import { ipcMain } from "electron";
import type { CatalogRealtimeSyncService } from "./catalogRealtimeSync.js";
import type { CatalogSyncService } from "./catalogSync.js";
import type { DownloadManager } from "./downloadManager.js";
import type { TrackDownloadQueue } from "./trackDownloadQueue.js";
import { LocalDatabase } from "./database.js";
import type { PlaybackPowerBlocker } from "./playbackPowerBlocker.js";
import type { ProfileSyncService } from "./profileSync.js";
import type { ProgressSyncService } from "./progressSync.js";
import type { EnqueueDownloadRequest } from "../shared/downloadQueueState.js";
import type { DownloadPriority } from "../shared/downloadQueuePolicy.js";
import type { SessionService } from "./sessionService.js";
import { isSafeStorageId } from "../shared/safeLocalPaths.js";

function assertSafeDownloadIds(bookId: string, trackId: string): boolean {
  return isSafeStorageId(bookId) && isSafeStorageId(trackId);
}

export interface IpcHandlerDeps {
  sessionService: SessionService;
  catalogSync: CatalogSyncService;
  catalogRealtimeSync: CatalogRealtimeSyncService;
  downloadManager: DownloadManager;
  trackDownloadQueue: TrackDownloadQueue;
  profileSync: ProfileSyncService;
  progressSync: ProgressSyncService;
  powerBlocker: PlaybackPowerBlocker;
  downloadsRoot: string;
}

export function registerIpcHandlers(deps: IpcHandlerDeps): void {
  const {
    sessionService,
    catalogSync,
    catalogRealtimeSync,
    downloadManager,
    trackDownloadQueue,
    profileSync,
    progressSync,
    powerBlocker,
    downloadsRoot,
  } = deps;

  ipcMain.handle("session:get", async () => {
    await sessionService.refreshIfNeeded();
    await sessionService.syncProfileFromServer();
    await Promise.all([
      catalogRealtimeSync.updateAuth(),
      profileSync.updateAuth(),
      progressSync.updateAuth(),
    ]);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:setOnline", async (_e, online: boolean) => {
    sessionService.setOnline(online);
    trackDownloadQueue.setOnline(online);
    if (!online || !sessionService.getSession()) return;
    await sessionService.refreshIfNeeded();
    await Promise.all([
      catalogRealtimeSync.updateAuth(),
      profileSync.updateAuth(),
      progressSync.updateAuth(),
    ]);
  });
  ipcMain.handle("session:login", async (_e, email: string, password: string) => {
    const session = await sessionService.login(email, password);
    await Promise.all([
      profileSync.start(session),
      progressSync.start(session),
      catalogRealtimeSync.start(session),
    ]);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:logout", () => {
    profileSync.stop();
    progressSync.stop();
    catalogRealtimeSync.stop();
    sessionService.logout();
  });
  ipcMain.handle("session:updateProfile", async (_e, displayName: string) => {
    const result = await sessionService.updateProfile(displayName);
    return { ...sessionService.getSnapshot(), ...result };
  });
  ipcMain.handle("session:changePassword", async (_e, newPassword: string) => {
    await sessionService.changePassword(newPassword);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:uploadAvatar", async (_e, jpegBytes: Uint8Array | number[]) => {
    await sessionService.uploadAvatar(jpegBytes);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("catalog:sync", () => catalogSync.syncCatalog());
  ipcMain.handle("db:getBooks", () => LocalDatabase.getBooks());
  ipcMain.handle("db:getCycles", () => LocalDatabase.getCycles());
  ipcMain.handle("db:getLibrarySnapshot", () => LocalDatabase.getLibrarySnapshot(downloadsRoot));
  ipcMain.handle("db:getAllTracks", () => LocalDatabase.getAllTracks());
  ipcMain.handle("db:getAllProgress", () => LocalDatabase.getAllProgress());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
  ipcMain.handle("download:track", (_e, bookId: string, trackId: string) => {
    if (!assertSafeDownloadIds(bookId, trackId)) {
      return Promise.reject(new Error("__download_invalid_path__"));
    }
    return downloadManager.downloadTrack(bookId, trackId);
  });
  ipcMain.handle("download:delete", (_e, bookId: string, trackId: string) => {
    if (!assertSafeDownloadIds(bookId, trackId)) {
      return Promise.reject(new Error("__download_invalid_path__"));
    }
    return downloadManager.deleteLocalTrack(bookId, trackId);
  });
  ipcMain.handle("download:list", () => downloadManager.listDownloadSummaries());
  ipcMain.handle("download:storageStats", () => downloadManager.getStorageStats());
  ipcMain.handle("download:deleteAll", () => downloadManager.deleteAll());
  ipcMain.handle("download:enqueue", (_e, request: EnqueueDownloadRequest) => {
    if (!assertSafeDownloadIds(request.bookId, request.trackId)) return;
    trackDownloadQueue.enqueue(request);
  });
  ipcMain.handle(
    "download:enqueueBatch",
    (_e, requests: EnqueueDownloadRequest[], batchId?: string) => {
      const safe = requests.filter((request) =>
        assertSafeDownloadIds(request.bookId, request.trackId),
      );
      if (safe.length === 0) return;
      trackDownloadQueue.enqueueBatch(safe, batchId);
    },
  );
  ipcMain.handle(
    "download:awaitTrack",
    (
      _e,
      bookId: string,
      trackId: string,
      options?: {
        priority?: DownloadPriority;
        title?: string;
        subtitle?: string | null;
        contentType?: string;
      },
    ) => {
      if (!assertSafeDownloadIds(bookId, trackId)) return Promise.resolve("FAILED");
      return trackDownloadQueue.awaitTrack(bookId, trackId, options);
    },
  );
  ipcMain.handle("download:cancelTrack", async (_e, bookId: string, trackId: string) => {
    if (!assertSafeDownloadIds(bookId, trackId)) return;
    await trackDownloadQueue.cancelTrack(bookId, trackId);
  });
  ipcMain.handle("download:cancelBatch", (_e, batchId: string) => {
    trackDownloadQueue.cancelBatch(batchId);
  });
  ipcMain.handle("download:cancelAll", () => trackDownloadQueue.cancelAll());
  ipcMain.handle("download:getQueueState", () => trackDownloadQueue.getQueueState());
  ipcMain.handle("sync:status", () => progressSync.getSyncStatus());
  ipcMain.handle("sync:trigger", () => progressSync.triggerSync());
  ipcMain.handle("progress:get", (_e, bookId: string) => LocalDatabase.getProgress(bookId));
  ipcMain.handle("progress:save", async (_e, bookId: string, trackId: string, positionMs: number) => {
    await progressSync.saveLocal(bookId, trackId, positionMs);
  });
  ipcMain.handle("playback:setActive", (_e, active: boolean) => {
    powerBlocker.setActive(active);
  });
}
