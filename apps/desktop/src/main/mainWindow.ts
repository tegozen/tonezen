import { app, BrowserWindow } from "electron";
import path from "node:path";
import {
  APP_SHELL_MIN_HEIGHT_PX,
  APP_SHELL_WIDTH_PX,
  mainWindowContentSize,
  needsMainWindowWidthEnforcement,
  normalizeMainWindowContentSize,
} from "../shared/appShell.js";
import { appIconPath } from "./assets.js";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

function enforceMainWindowShellSize(mainWindow: BrowserWindow): void {
  mainWindow.setMinimumSize(APP_SHELL_WIDTH_PX, APP_SHELL_MIN_HEIGHT_PX);
  mainWindow.setMaximumSize(APP_SHELL_WIDTH_PX, 100_000);

  const [contentWidth, contentHeight] = mainWindow.getContentSize();
  if (!needsMainWindowWidthEnforcement(contentWidth)) return;

  const next = normalizeMainWindowContentSize(contentHeight);
  mainWindow.setContentSize(next.width, next.height);
}

function hardenMainWindowNavigation(mainWindow: BrowserWindow): void {
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  mainWindow.webContents.on("will-navigate", (event, url) => {
    const current = mainWindow.webContents.getURL();
    if (url !== current) event.preventDefault();
  });
  mainWindow.webContents.on("will-attach-webview", (event) => {
    event.preventDefault();
  });
}

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
    minHeight: APP_SHELL_MIN_HEIGHT_PX,
    show: false,
    backgroundColor: "#00142B",
    icon: appIconPath,
    title: "Tonezen",
    webPreferences: {
      preload: path.join(__dirname, "../preload/index.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
    },
  });

  hardenMainWindowNavigation(mainWindow);

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

  mainWindow.on("show", () => {
    enforceMainWindowShellSize(mainWindow);
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
