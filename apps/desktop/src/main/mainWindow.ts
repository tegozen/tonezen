import { app, BrowserWindow } from "electron";
import path from "node:path";
import { APP_SHELL_WIDTH_PX, mainWindowContentSize } from "../shared/appShell.js";
import { appIconPath } from "./assets.js";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

export function createMainWindow(
  lifecycle: WindowLifecycleManager,
  onReadyToShow?: () => void,
): BrowserWindow {
  const { width, height } = mainWindowContentSize();
  const mainWindow = new BrowserWindow({
    width,
    height,
    useContentSize: true,
    minWidth: APP_SHELL_WIDTH_PX,
    maxWidth: APP_SHELL_WIDTH_PX,
    minHeight: 640,
    show: false,
    backgroundColor: "#00142B",
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
