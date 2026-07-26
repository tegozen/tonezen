import { net } from "electron";
import {
  PROGRESS_SPLASH_PULL_TIMEOUT_MS,
  type ProgressSyncService,
} from "../progress/progressSync.js";
import type { SessionService } from "../session/sessionService.js";

async function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T | null> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<null>((resolve) => {
        timer = setTimeout(() => resolve(null), ms);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

export async function runColdStartBootstrap(
  sessionService: SessionService,
  progressSync: ProgressSyncService,
): Promise<void> {
  const online = net.isOnline();
  sessionService.setOnline(online);
  const session = sessionService.getSession();
  progressSync.bindUser(session);

  // Offline: never block splash on network — local catalog/progress/downloads win.
  if (!online) {
    progressSync.prepareHydrateFromLocalCache();
    return;
  }

  await withTimeout(sessionService.refreshIfNeeded(), PROGRESS_SPLASH_PULL_TIMEOUT_MS);

  const refreshed = sessionService.getSession();
  progressSync.bindUser(refreshed);
  if (!refreshed) return;

  const snapshot = sessionService.getSnapshot();
  if (snapshot.state === "Unauthenticated") return;
  if (!sessionService.isAccessTokenUsable()) return;

  progressSync.prepareHydrateFromLocalCache();
  // Bounded pull — timeout fails open to local cache; background start retries later.
  await withTimeout(progressSync.pullAll(), PROGRESS_SPLASH_PULL_TIMEOUT_MS);
  await progressSync.flushPending();
}
