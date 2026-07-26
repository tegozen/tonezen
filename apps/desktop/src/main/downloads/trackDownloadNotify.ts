import type { BrowserWindow } from "electron";
import type { DownloadAwaitResult, DownloadQueueState } from "@core/downloads/downloadQueueState.js";
import type { DownloadQueueRow } from "./downloadQueueDb.js";
import type { DiagnosticErrorEntry } from "../app/diagnosticsLog.js";

type DownloadFailureLogger = (entry: DiagnosticErrorEntry) => void | Promise<unknown>;

export function broadcastQueueState(
  mainWindow: BrowserWindow | null,
  state: DownloadQueueState,
): void {
  const window = mainWindow;
  if (!window || window.isDestroyed()) return;
  window.webContents.send("download:queueState", state);
}

export function notifyCatalogUpdated(mainWindow: BrowserWindow | null): void {
  const window = mainWindow;
  if (!window || window.isDestroyed()) return;
  window.webContents.send("catalog:updated");
}

export function reportDownloadFailure(
  mainWindow: BrowserWindow | null,
  logDownloadFailure: DownloadFailureLogger | undefined,
  entity: DownloadQueueRow,
  code: DownloadAwaitResult,
): void {
  const entry: DiagnosticErrorEntry = {
    area: "download",
    message: "Не удалось скачать",
    code,
    bookId: entity.bookId,
    trackId: entity.trackId,
    bookTitle: entity.subtitle ?? undefined,
    trackTitle: entity.title,
  };
  try {
    void Promise.resolve(logDownloadFailure?.(entry)).catch(() => {});
  } catch {
    // Diagnostics must never block the download worker.
  }
  const window = mainWindow;
  if (!window || window.isDestroyed()) return;
  window.webContents.send("download:failed", entry);
}
