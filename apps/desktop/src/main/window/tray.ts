import { app, Menu, Tray, nativeImage, type BrowserWindow } from "electron";
import { appIconPngPath, trayIconPath } from "../app/assets.js";
import type { WindowLifecycleManager } from "./windowLifecycle.js";

/** macOS tray clicks fire twice; show the menu instead of toggling the window. */
export function handleTrayIconClick(input: {
  platform: NodeJS.Platform;
  isWindowVisible: boolean;
  showWindow: () => void;
  hideWindow: () => void;
  popUpMenu: () => void;
}): void {
  if (input.platform === "darwin") {
    input.popUpMenu();
    return;
  }
  if (input.isWindowVisible) {
    input.hideWindow();
  } else {
    input.showWindow();
  }
}

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
  const menu = buildTrayMenu(mainWindow, lifecycle);
  tray.setContextMenu(menu);
  tray.on("click", (_event, bounds) => {
    handleTrayIconClick({
      platform: process.platform,
      isWindowVisible: mainWindow.isVisible(),
      showWindow: () => {
        mainWindow.show();
        if (process.platform === "darwin") app.dock?.show();
      },
      hideWindow: () => {
        mainWindow.hide();
        if (process.platform === "darwin") app.dock?.hide();
      },
      popUpMenu: () => {
        tray.popUpContextMenu(
          menu,
          bounds ? { x: bounds.x, y: bounds.y } : undefined,
        );
      },
    });
  });
  return tray;
}
