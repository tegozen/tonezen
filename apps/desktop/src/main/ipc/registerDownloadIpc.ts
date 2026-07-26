import { ipcMain } from "electron";
import type { EnqueueDownloadRequest } from "@core/downloads/downloadQueueState.js";
import type { DownloadPriority } from "@core/downloads/downloadQueuePolicy.js";
import { isSafeStorageId } from "@core/platform/safeLocalPaths.js";
import type { IpcHandlerDeps } from "./ipcHandlers.js";

function assertSafeDownloadIds(bookId: string, trackId: string): boolean {
  return isSafeStorageId(bookId) && isSafeStorageId(trackId);
}

export function registerDownloadIpc(deps: IpcHandlerDeps): void {
  const { downloadManager, trackDownloadQueue } = deps;

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
}
