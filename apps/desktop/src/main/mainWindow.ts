import { app, BrowserWindow } from "electron";
import path from "node:path";
import { appIconPath } from "./assets.js";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

export function createMainWindow(lifecycle: WindowLifecycleManager, onReadyToShow?: () => void): BrowserWindow {
  const mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    show: false,
    backgroundColor: "#020617",
    icon: appIconPath,
    title: "Tonezen",
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

  mainWindow.once("ready-to-show", () => {
    onReadyToShow?.();
    mainWindow.show();
  });

  return mainWindow;
}
