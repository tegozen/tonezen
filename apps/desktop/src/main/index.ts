import { app, BrowserWindow, Menu, Tray, nativeImage, ipcMain, powerSaveBlocker } from "electron";
import path from "node:path";
import { WindowLifecycleManager } from "./windowLifecycle.js";
import { SessionService } from "./sessionService.js";
import { LocalDatabase } from "./database.js";
import { CatalogSyncService } from "./catalogSync.js";
import { DownloadManager } from "./downloadManager.js";

const lifecycle = new WindowLifecycleManager();
const sessionService = new SessionService();

const API_BASE_URL = process.env.TPLAYER_API_URL ?? "http://localhost:8000/api/v1";
const SUPABASE_URL = process.env.TPLAYER_SUPABASE_URL ?? "http://localhost:8000";
const SUPABASE_ANON_KEY =
  process.env.TPLAYER_SUPABASE_ANON_KEY ??
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0";

let catalogSync: CatalogSyncService;
let downloadManager: DownloadManager;
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
      if (process.platform === "darwin") app.dock?.hide();
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
    if (mainWindow?.isVisible()) mainWindow.hide();
    else {
      mainWindow?.show();
      if (process.platform === "darwin") app.dock?.show();
    }
  });
}

app.whenReady().then(() => {
  const userData = app.getPath("userData");
  LocalDatabase.init(userData);
  sessionService.init(userData, { supabaseUrl: SUPABASE_URL, anonKey: SUPABASE_ANON_KEY });
  catalogSync = new CatalogSyncService(API_BASE_URL, () => sessionService.getAccessToken());
  downloadManager = new DownloadManager(
    path.join(userData, "downloads"),
    API_BASE_URL,
    () => sessionService.getAccessToken(),
  );
  createTray();
  createWindow();
  registerIpc();
});

app.on("window-all-closed", () => {
  // tray-first: keep process alive
});

app.on("before-quit", () => {
  lifecycle.setQuitting(true);
  if (powerBlockerId != null) powerSaveBlocker.stop(powerBlockerId);
});

function registerIpc(): void {
  ipcMain.handle("session:get", async () => {
    await sessionService.refreshIfNeeded();
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:setOnline", (_e, online: boolean) => {
    sessionService.setOnline(online);
  });
  ipcMain.handle("session:login", async (_e, email: string, password: string) => {
    await sessionService.login(email, password);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:logout", () => sessionService.logout());
  ipcMain.handle("catalog:sync", async () => {
    const books = await catalogSync.fetchBooks();
    LocalDatabase.upsertBooks(books);
    for (const book of books) {
      const headers: Record<string, string> = {};
      const token = sessionService.getAccessToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      const res = await fetch(`${API_BASE_URL}/catalog/books/${book.id}`, { headers });
      const detail = (await res.json()) as {
        tracks: Array<{
          id: string;
          sort_order: number;
          title: string;
          filename: string;
          duration_ms?: number;
        }>;
      };
      LocalDatabase.upsertTracks(
        (detail.tracks ?? []).map((t) => ({
          id: t.id,
          bookId: book.id,
          sortOrder: t.sort_order,
          title: t.title,
          filename: t.filename,
          durationMs: t.duration_ms,
        })),
      );
    }
    return LocalDatabase.getBooks();
  });
  ipcMain.handle("db:getBooks", () => LocalDatabase.getBooks());
  ipcMain.handle("db:getTracks", (_e, bookId: string) => LocalDatabase.getTracks(bookId));
  ipcMain.handle("download:track", (_e, bookId: string, trackId: string) =>
    downloadManager.downloadTrack(bookId, trackId),
  );
  ipcMain.handle("download:delete", (_e, bookId: string, trackId: string) => {
    downloadManager.deleteLocalTrack(bookId, trackId);
  });
  ipcMain.handle("playback:setActive", (_e, active: boolean) => {
    if (active && powerBlockerId == null) {
      powerBlockerId = powerSaveBlocker.start("prevent-app-suspension");
    } else if (!active && powerBlockerId != null) {
      powerSaveBlocker.stop(powerBlockerId);
      powerBlockerId = null;
    }
  });
}
