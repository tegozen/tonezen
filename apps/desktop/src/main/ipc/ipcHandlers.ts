import type { CatalogRealtimeSyncService } from "../catalog/catalogRealtimeSync.js";
import type { CatalogSyncService } from "../catalog/catalogSync.js";
import type { DownloadManager } from "../downloads/downloadManager.js";
import type { TrackDownloadQueue } from "../downloads/trackDownloadQueue.js";
import type { PlaybackPowerBlocker } from "../media/playbackPowerBlocker.js";
import type { ProfileSyncService } from "../profile/profileSync.js";
import type { ProgressSyncService } from "../progress/progressSync.js";
import type { SessionService } from "../session/sessionService.js";
import { registerAppIpc } from "./registerAppIpc.js";
import { registerCatalogIpc } from "./registerCatalogIpc.js";
import { registerDownloadIpc } from "./registerDownloadIpc.js";
import { registerProgressIpc } from "./registerProgressIpc.js";
import { registerSessionIpc } from "./registerSessionIpc.js";

export interface IpcHandlerDeps {
  sessionService: SessionService;
  catalogSync: CatalogSyncService;
  catalogRealtimeSync: CatalogRealtimeSyncService;
  downloadManager: DownloadManager;
  trackDownloadQueue: TrackDownloadQueue;
  profileSync: ProfileSyncService;
  progressSync: ProgressSyncService;
  powerBlocker: PlaybackPowerBlocker;
  downloadsRoot: string;
  documentsPath: string;
}

export function registerIpcHandlers(deps: IpcHandlerDeps): void {
  registerSessionIpc(deps);
  registerCatalogIpc(deps);
  registerDownloadIpc(deps);
  registerProgressIpc(deps);
  registerAppIpc(deps);
}
