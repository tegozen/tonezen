import { ipcMain } from "electron";
import { appendDiagnosticError, type DiagnosticErrorEntry } from "../app/diagnosticsLog.js";
import type { IpcHandlerDeps } from "./ipcHandlers.js";

export function registerAppIpc(deps: IpcHandlerDeps): void {
  const { powerBlocker, documentsPath } = deps;

  ipcMain.handle("diagnostics:logError", (_e, entry: DiagnosticErrorEntry) =>
    appendDiagnosticError(documentsPath, entry),
  );
  ipcMain.handle("playback:setActive", (_e, active: boolean) => {
    powerBlocker.setActive(active);
  });
}
