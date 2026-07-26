import { ipcMain } from "electron";
import { LocalDatabase } from "../db/localDatabase.js";
import type { IpcHandlerDeps } from "./ipcHandlers.js";

export function registerCatalogIpc(deps: IpcHandlerDeps): void {
  const { catalogSync, downloadsRoot } = deps;

  ipcMain.handle("catalog:sync", () => catalogSync.syncCatalog());
  ipcMain.handle("db:getBooks", () => LocalDatabase.getBooks());
  ipcMain.handle("db:getCycles", () => LocalDatabase.getCycles());
  ipcMain.handle(
    "db:getLibrarySnapshot",
    (_e, options?: { reconcileLocalPaths?: boolean }) =>
      LocalDatabase.getLibrarySnapshot(downloadsRoot, options),
  );
  ipcMain.handle("db:getAllTracks", () => LocalDatabase.getAllTracks());
  ipcMain.handle("db:getAllProgress", () => LocalDatabase.getAllProgress());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
}
