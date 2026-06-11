import { app, BrowserWindow, Menu, Tray, nativeImage, ipcMain, powerSaveBlocker } from "electron";
import path from "node:path";
import { WindowLifecycleManager } from "./windowLifecycle.js";
import { SessionService } from "./sessionService.js";
import { LocalDatabase } from "./database.js";

const lifecycle = new WindowLifecycleManager();
const sessionService = new SessionService();
let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let powerBlockerId: number | null = null;

function createWindow(): void {
  mainWindow = new BrowserWindow({
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
      mainWindow?.hide();
      if (process.platform === "darwin") {
        app.dock?.hide();
      }
    }
  });

  mainWindow.on("minimize", (event) => {
    if (lifecycle.shouldHideOnMinimize()) {
      event.preventDefault();
      mainWindow?.hide();
    }
  });

  if (process.env.ELECTRON_RENDERER_URL) {
    mainWindow.loadURL(process.env.ELECTRON_RENDERER_URL);
  } else {
    mainWindow.loadFile(path.join(__dirname, "../renderer/index.html"));
  }
}

function buildTrayMenu(): Menu {
  return Menu.buildFromTemplate([
    {
      label: "Open",
      click: () => {
        mainWindow?.show();
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

function createTray(): void {
  const icon = nativeImage.createEmpty();
  tray = new Tray(icon);
  tray.setToolTip("TPlayer");
  tray.setContextMenu(buildTrayMenu());
  tray.on("click", () => {
    if (mainWindow?.isVisible()) {
      mainWindow.hide();
    } else {
      mainWindow?.show();
      if (process.platform === "darwin") app.dock?.show();
    }
  });
}

app.whenReady().then(() => {
  const userData = app.getPath("userData");
  LocalDatabase.init(userData);
  sessionService.init(userData);
  createTray();
  createWindow();
  registerIpc();
});

app.on("window-all-closed", () => {
  // Keep running in tray — do not quit on window close
});

app.on("before-quit", () => {
  lifecycle.setQuitting(true);
  if (powerBlockerId != null) {
    powerSaveBlocker.stop(powerBlockerId);
  }
});

function registerIpc(): void {
  ipcMain.handle("session:get", () => sessionService.getSnapshot());
  ipcMain.handle("session:login", (_e, email: string, password: string) =>
    sessionService.loginDemo(email, password),
  );
  ipcMain.handle("session:logout", () => sessionService.logout());
  ipcMain.handle("session:refreshIfNeeded", () => sessionService.refreshIfNeeded());
  ipcMain.handle("db:getBooks", () => LocalDatabase.getBooks());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
  ipcMain.handle("playback:setActive", (_e, active: boolean) => {
    if (active && powerBlockerId == null) {
      powerBlockerId = powerSaveBlocker.start("prevent-app-suspension");
    } else if (!active && powerBlockerId != null) {
      powerSaveBlocker.stop(powerBlockerId);
      powerBlockerId = null;
    }
  });
}
