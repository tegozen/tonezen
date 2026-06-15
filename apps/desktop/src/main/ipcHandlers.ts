import { ipcMain } from "electron";
import type { CatalogRealtimeSyncService } from "./catalogRealtimeSync.js";
import type { CatalogSyncService } from "./catalogSync.js";
import type { DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "./database.js";
import type { PlaybackPowerBlocker } from "./playbackPowerBlocker.js";
import type { ProfileSyncService } from "./profileSync.js";
import type { ProgressSyncService } from "./progressSync.js";
import type { SessionService } from "./sessionService.js";

export interface IpcHandlerDeps {
  sessionService: SessionService;
  catalogSync: CatalogSyncService;
  catalogRealtimeSync: CatalogRealtimeSyncService;
  downloadManager: DownloadManager;
  profileSync: ProfileSyncService;
  progressSync: ProgressSyncService;
  powerBlocker: PlaybackPowerBlocker;
}

export function registerIpcHandlers(deps: IpcHandlerDeps): void {
  const {
    sessionService,
    catalogSync,
    catalogRealtimeSync,
    downloadManager,
    profileSync,
    progressSync,
    powerBlocker,
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
  ipcMain.handle("session:setOnline", (_e, online: boolean) => {
    sessionService.setOnline(online);
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
  ipcMain.handle("db:getLibrarySnapshot", () => LocalDatabase.getLibrarySnapshot());
  ipcMain.handle("db:getAllTracks", () => LocalDatabase.getAllTracks());
  ipcMain.handle("db:getAllProgress", () => LocalDatabase.getAllProgress());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
  ipcMain.handle("download:track", (_e, bookId: string, trackId: string) =>
    downloadManager.downloadTrack(bookId, trackId),
  );
  ipcMain.handle("download:delete", (_e, bookId: string, trackId: string) =>
    downloadManager.deleteLocalTrack(bookId, trackId),
  );
  ipcMain.handle("download:list", () => downloadManager.listDownloadSummaries());
  ipcMain.handle("download:storageStats", () => downloadManager.getStorageStats());
  ipcMain.handle("download:deleteAll", () => downloadManager.deleteAll());
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
