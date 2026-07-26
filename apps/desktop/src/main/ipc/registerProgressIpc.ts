import { ipcMain } from "electron";
import { LocalDatabase } from "../db/localDatabase.js";
import type { IpcHandlerDeps } from "./ipcHandlers.js";

export function registerProgressIpc(deps: IpcHandlerDeps): void {
  const { progressSync } = deps;

  ipcMain.handle("sync:status", () => progressSync.getSyncStatus());
  ipcMain.handle("sync:trigger", () => progressSync.triggerSync());
  ipcMain.handle("progress:get", (_e, bookId: string) => LocalDatabase.getProgress(bookId));
  ipcMain.handle("progress:save", async (_e, bookId: string, trackId: string, positionMs: number) => {
    await progressSync.saveLocal(bookId, trackId, positionMs);
  });
  ipcMain.handle("progress:chooseLocal", async (_e, bookId: string) => {
    return progressSync.chooseLocalProgress(bookId);
  });
  ipcMain.handle("progress:chooseServer", async (_e, bookId: string) => {
    return progressSync.chooseServerProgress(bookId);
  });
}
