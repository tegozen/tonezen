import { app, Menu, Tray, nativeImage, type BrowserWindow } from "electron";
import { appIconPngPath, trayIconPath } from "./assets.js";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

function buildTrayMenu(mainWindow: BrowserWindow, lifecycle: WindowLifecycleManager): Menu {
  return Menu.buildFromTemplate([
    {
      label: "Открыть",
      click: () => {
        mainWindow.show();
        if (process.platform === "darwin") app.dock?.show();
      },
    },
    { type: "separator" },
    {
      label: "Выход",
      click: () => {
        lifecycle.setQuitting(true);
        app.quit();
      },
    },
  ]);
}

export function createAppTray(mainWindow: BrowserWindow, lifecycle: WindowLifecycleManager): Tray {
  const trayIcon = nativeImage.createFromPath(trayIconPath);
  const icon = trayIcon.isEmpty() ? nativeImage.createFromPath(appIconPngPath) : trayIcon;
  const tray = new Tray(icon);
  tray.setToolTip("Tonezen");
  tray.setContextMenu(buildTrayMenu(mainWindow, lifecycle));
  tray.on("click", () => {
    if (mainWindow.isVisible()) mainWindow.hide();
    else {
      mainWindow.show();
      if (process.platform === "darwin") app.dock?.show();
    }
  });
  return tray;
}
