import { app, Menu } from "electron";
import type { BrowserWindow } from "electron";
import path from "node:path";
import { registerLocalAudioScheme, setupLocalAudioProtocol } from "../media/mediaProtocol.js";
import { WindowLifecycleManager } from "../window/windowLifecycle.js";
import { SessionService } from "../session/sessionService.js";
import { LocalDatabase } from "../db/localDatabase.js";
import { CatalogSyncService } from "../catalog/catalogSync.js";
import { CatalogRealtimeSyncService } from "../catalog/catalogRealtimeSync.js";
import { DownloadManager } from "../downloads/downloadManager.js";
import { ProfileSyncService } from "../profile/profileSync.js";
import { ProgressSyncService } from "../progress/progressSync.js";
import { getClientConfig, loadAppEnv, loadPackagedEnv } from "./loadEnv.js";
import { createMainWindow } from "../window/mainWindow.js";
import { closeSplashWindow, createSplashWindow } from "../window/splashWindow.js";
import { runColdStartBootstrap } from "./bootstrap.js";
import { createAppTray } from "../window/tray.js";
import { PlaybackPowerBlocker } from "../media/playbackPowerBlocker.js";
import { registerIpcHandlers } from "../ipc/ipcHandlers.js";
import { TrackDownloadQueue } from "../downloads/trackDownloadQueue.js";
import { AppUiReferences } from "../window/appUiReferences.js";
import { appendDiagnosticError } from "./diagnosticsLog.js";

loadAppEnv();
registerLocalAudioScheme();
app.setAppUserModelId("com.tonezen.desktop");

const lifecycle = new WindowLifecycleManager();
const sessionService = new SessionService();
const powerBlocker = new PlaybackPowerBlocker();
const appUiReferences = new AppUiReferences();

let catalogSync: CatalogSyncService;
let catalogRealtimeSync: CatalogRealtimeSyncService;
let downloadManager: DownloadManager;
let trackDownloadQueue: TrackDownloadQueue;
let profileSync: ProfileSyncService;
let progressSync: ProgressSyncService;

const hasSingleInstanceLock = app.requestSingleInstanceLock();

if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on("second-instance", () => {
    const didShowMainWindow = appUiReferences.showMainWindow();
    if (didShowMainWindow && process.platform === "darwin") app.dock?.show();
  });

  app.whenReady().then(async () => {
    Menu.setApplicationMenu(null);

    const splashWindow = createSplashWindow();
    appUiReferences.setSplashWindow(splashWindow);
    if (app.isPackaged) {
      loadPackagedEnv(process.execPath);
    }
    const runtimeConfig = getClientConfig();
    const userData = app.getPath("userData");
    const documentsPath = app.getPath("documents");
    const downloadsRoot = path.join(userData, "downloads");
    setupLocalAudioProtocol([downloadsRoot]);
    LocalDatabase.init(userData);
    sessionService.init(userData, {
      baseUrl: runtimeConfig.baseUrl,
      anonKey: runtimeConfig.supabaseAnonKey,
    });
    catalogSync = new CatalogSyncService(runtimeConfig.baseUrl, () =>
      sessionService.getAccessToken(),
    );
    catalogRealtimeSync = new CatalogRealtimeSyncService(
      catalogSync,
      {
        baseUrl: runtimeConfig.baseUrl,
        anonKey: runtimeConfig.supabaseAnonKey,
      },
      () => sessionService.getAccessToken(),
      () => sessionService.refreshIfNeeded(),
      () => sessionService.isAccessTokenUsable(),
    );
    downloadManager = new DownloadManager(
      downloadsRoot,
      runtimeConfig.baseUrl,
      () => sessionService.getAccessToken(),
    );
    trackDownloadQueue = new TrackDownloadQueue(
      downloadsRoot,
      downloadManager,
      sessionService,
      (entry) => appendDiagnosticError(documentsPath, entry),
    );
    await trackDownloadQueue.restoreFromDb();
    profileSync = new ProfileSyncService(sessionService, {
      baseUrl: runtimeConfig.baseUrl,
      anonKey: runtimeConfig.supabaseAnonKey,
    });
    progressSync = new ProgressSyncService(
      () => sessionService.getAccessToken(),
      () => sessionService.refreshIfNeeded(),
      () => sessionService.isAccessTokenUsable(),
      {
        baseUrl: runtimeConfig.baseUrl,
        anonKey: runtimeConfig.supabaseAnonKey,
      },
    );

    await runColdStartBootstrap(sessionService, progressSync);

    const mainWindow = createMainWindow(lifecycle, () => closeSplashWindow(splashWindow));
    appUiReferences.setMainWindow(mainWindow);
    trackDownloadQueue.setMainWindow(mainWindow);
    appUiReferences.setTray(createAppTray(mainWindow, lifecycle));
    profileSync.setMainWindow(mainWindow);
    progressSync.setMainWindow(mainWindow);
    catalogRealtimeSync.setMainWindow(mainWindow);
    void startRealtimeSyncIfNeeded(mainWindow);
    registerIpcHandlers({
      sessionService,
      catalogSync,
      catalogRealtimeSync,
      downloadManager,
      trackDownloadQueue,
      profileSync,
      progressSync,
      powerBlocker,
      downloadsRoot,
      documentsPath,
    });
  });
}

async function startRealtimeSyncIfNeeded(mainWindow: BrowserWindow): Promise<void> {
  await sessionService.refreshIfNeeded();
  const session = sessionService.getSession();
  if (!session) return;

  await Promise.all([
    profileSync.start(session),
    progressSync.start(session),
    catalogRealtimeSync.start(session),
  ]);

  try {
    await catalogSync.syncCatalog();
    if (!mainWindow.isDestroyed()) {
      mainWindow.webContents.send("catalog:updated");
    }
  } catch (err) {
    console.error("[catalog] startup sync failed:", err);
  }
}

app.on("window-all-closed", () => {
  // tray-first: keep process alive
});

app.on("before-quit", () => {
  lifecycle.setQuitting(true);
  powerBlocker.stop();
});
