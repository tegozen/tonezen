import { app } from "electron";
import path from "node:path";
import { WindowLifecycleManager } from "./windowLifecycle.js";
import { SessionService } from "./sessionService.js";
import { LocalDatabase } from "./database.js";
import { CatalogSyncService } from "./catalogSync.js";
import { DownloadManager } from "./downloadManager.js";
import { ProgressSyncService } from "./progressSync.js";
import { getClientConfig, loadAppEnv, loadPackagedEnv } from "./loadEnv.js";
import { createMainWindow } from "./mainWindow.js";
import { closeSplashWindow, createSplashWindow } from "./splashWindow.js";
import { createAppTray } from "./tray.js";
import { PlaybackPowerBlocker } from "./playbackPowerBlocker.js";
import { registerIpcHandlers } from "./ipcHandlers.js";

loadAppEnv();
app.setAppUserModelId("com.tonezen.desktop");

const lifecycle = new WindowLifecycleManager();
const sessionService = new SessionService();
const powerBlocker = new PlaybackPowerBlocker();

let catalogSync: CatalogSyncService;
let downloadManager: DownloadManager;
let progressSync: ProgressSyncService;

app.whenReady().then(() => {
  const splashWindow = createSplashWindow();
  if (app.isPackaged) {
    loadPackagedEnv(process.execPath);
  }
  const runtimeConfig = getClientConfig();
  const userData = app.getPath("userData");
  LocalDatabase.init(userData);
  sessionService.init(userData, {
    baseUrl: runtimeConfig.baseUrl,
    anonKey: runtimeConfig.supabaseAnonKey,
  });
  catalogSync = new CatalogSyncService(runtimeConfig.baseUrl, () => sessionService.getAccessToken());
  downloadManager = new DownloadManager(
    path.join(userData, "downloads"),
    runtimeConfig.baseUrl,
    () => sessionService.getAccessToken(),
  );
  progressSync = new ProgressSyncService(
    () => sessionService.getSession(),
    () => sessionService.getAccessToken(),
    () => sessionService.refreshIfNeeded(),
    {
      baseUrl: runtimeConfig.baseUrl,
      anonKey: runtimeConfig.supabaseAnonKey,
    },
  );

  const mainWindow = createMainWindow(lifecycle, () => closeSplashWindow(splashWindow));
  createAppTray(mainWindow, lifecycle);
  progressSync.setMainWindow(mainWindow);
  void startProgressSyncIfNeeded();
  registerIpcHandlers({
    sessionService,
    catalogSync,
    downloadManager,
    progressSync,
    powerBlocker,
  });
});

async function startProgressSyncIfNeeded(): Promise<void> {
  await sessionService.refreshIfNeeded();
  const session = sessionService.getSession();
  if (session) {
    await progressSync.start(session);
  }
}

app.on("window-all-closed", () => {
  // tray-first: keep process alive
});

app.on("before-quit", () => {
  lifecycle.setQuitting(true);
  powerBlocker.stop();
});
