import { app, BrowserWindow } from "electron";
import path from "node:path";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

export function createMainWindow(lifecycle: WindowLifecycleManager): BrowserWindow {
  const mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    show: true,
    webPreferences: {
      preload: path.join(__dirname, "../preload/index.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.on("close", (event) => {
    if (lifecycle.shouldPreventClose()) {
      event.preventDefault();
      mainWindow.hide();
      if (process.platform === "darwin") app.dock?.hide();
    }
  });

  mainWindow.on("minimize", () => {
    if (lifecycle.shouldHideOnMinimize()) {
      mainWindow.hide();
    }
  });

  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    mainWindow.loadFile(path.join(__dirname, "../renderer/index.html"));
  }

  return mainWindow;
}
