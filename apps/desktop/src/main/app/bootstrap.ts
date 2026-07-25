import { net } from "electron";
import type { ProgressSyncService } from "../progress/progressSync.js";
import type { SessionService } from "../session/sessionService.js";

export async function runColdStartBootstrap(
  sessionService: SessionService,
  progressSync: ProgressSyncService,
): Promise<void> {
  const online = net.isOnline();
  sessionService.setOnline(online);
  await sessionService.refreshIfNeeded();

  const session = sessionService.getSession();
  if (!online || !session) return;

  const snapshot = sessionService.getSnapshot();
  if (snapshot.state === "Unauthenticated") return;
  if (!sessionService.isAccessTokenUsable()) return;

  await progressSync.pullAll();
  await progressSync.flushPending();
}
