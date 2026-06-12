import { app, Menu, Tray, nativeImage, type BrowserWindow } from "electron";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

function buildTrayMenu(mainWindow: BrowserWindow, lifecycle: WindowLifecycleManager): Menu {
  return Menu.buildFromTemplate([
    {
      label: "Open",
      click: () => {
        mainWindow.show();
        if (process.platform === "darwin") app.dock?.show();
      },
    },
    { type: "separator" },
    {
      label: "Exit",
      click: () => {
        lifecycle.setQuitting(true);
        app.quit();
      },
    },
  ]);
}

export function createAppTray(mainWindow: BrowserWindow, lifecycle: WindowLifecycleManager): Tray {
  const icon = nativeImage.createEmpty();
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
