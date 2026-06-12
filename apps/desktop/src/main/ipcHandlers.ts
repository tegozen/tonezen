import { ipcMain } from "electron";
import type { CatalogSyncService } from "./catalogSync.js";
import type { DownloadManager } from "./downloadManager.js";
import { LocalDatabase } from "./database.js";
import type { PlaybackPowerBlocker } from "./playbackPowerBlocker.js";
import type { ProgressSyncService } from "./progressSync.js";
import type { SessionService } from "./sessionService.js";

export interface IpcHandlerDeps {
  sessionService: SessionService;
  catalogSync: CatalogSyncService;
  downloadManager: DownloadManager;
  progressSync: ProgressSyncService;
  powerBlocker: PlaybackPowerBlocker;
}

export function registerIpcHandlers(deps: IpcHandlerDeps): void {
  const { sessionService, catalogSync, downloadManager, progressSync, powerBlocker } = deps;

  ipcMain.handle("session:get", async () => {
    await sessionService.refreshIfNeeded();
    await progressSync.updateAuth();
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:setOnline", (_e, online: boolean) => {
    sessionService.setOnline(online);
  });
  ipcMain.handle("session:login", async (_e, email: string, password: string) => {
    const session = await sessionService.login(email, password);
    await progressSync.start(session);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:logout", () => {
    progressSync.stop();
    sessionService.logout();
  });
  ipcMain.handle("catalog:sync", () => catalogSync.syncCatalog());
  ipcMain.handle("db:getBooks", () => LocalDatabase.getBooks());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
  ipcMain.handle("download:track", (_e, bookId: string, trackId: string) =>
    downloadManager.downloadTrack(bookId, trackId),
  );
  ipcMain.handle("download:delete", (_e, bookId: string, trackId: string) => {
    downloadManager.deleteLocalTrack(bookId, trackId);
  });
  ipcMain.handle("download:list", () => downloadManager.listDownloadSummaries());
  ipcMain.handle("download:storageStats", () => downloadManager.getStorageStats());
  ipcMain.handle("download:deleteAll", () => downloadManager.deleteAll());
  ipcMain.handle("favorites:list", () => LocalDatabase.getFavoriteBookIds());
  ipcMain.handle("favorites:toggle", (_e, bookId: string) => {
    LocalDatabase.toggleFavorite(bookId);
    return LocalDatabase.getFavoriteBookIds();
  });
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
