import { ipcMain } from "electron";
import { coerceAvatarJpegBytes } from "@core/profile/avatarBytes.js";
import type { IpcHandlerDeps } from "./ipcHandlers.js";
import { PROGRESS_SPLASH_PULL_TIMEOUT_MS } from "../progress/progressSync.js";

export function registerSessionIpc(deps: IpcHandlerDeps): void {
  const { sessionService, catalogRealtimeSync, profileSync, progressSync } = deps;

  ipcMain.handle("session:get", async () => {
    await sessionService.refreshIfNeeded();
    await sessionService.syncProfileFromServer();
    await Promise.all([
      catalogRealtimeSync.updateAuth(),
      profileSync.updateAuth(),
      progressSync.updateAuth(),
    ]);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:setOnline", async (_e, online: boolean) => {
    sessionService.setOnline(online);
    deps.trackDownloadQueue.setOnline(online);
    if (!online || !sessionService.getSession()) return;
    await sessionService.refreshIfNeeded();
    await Promise.all([
      catalogRealtimeSync.updateAuth(),
      profileSync.updateAuth(),
      progressSync.updateAuth(),
    ]);
    // Pull then flush once back online — pending local must not push before hydrate.
    await progressSync.triggerSync();
  });
  ipcMain.handle("session:login", async (_e, email: string, password: string) => {
    const session = await sessionService.login(email, password);
    await Promise.all([
      profileSync.start(session),
      progressSync.start(session, { splashTimeoutMs: PROGRESS_SPLASH_PULL_TIMEOUT_MS }),
      catalogRealtimeSync.start(session),
    ]);
    return sessionService.getSnapshot();
  });
  ipcMain.handle("session:verifyInviteCode", async (_e, code: string) => (
    sessionService.verifyInviteCode(code)
  ));
  ipcMain.handle(
    "session:register",
    async (
      _e,
      input: {
        inviteCode: string;
        email: string;
        password: string;
        displayName?: string;
      },
    ) => {
      const session = await sessionService.registerWithInvite(input);
      await Promise.all([
        profileSync.start(session),
        progressSync.start(session, { splashTimeoutMs: PROGRESS_SPLASH_PULL_TIMEOUT_MS }),
        catalogRealtimeSync.start(session),
      ]);
      return sessionService.getSnapshot();
    },
  );
  ipcMain.handle("session:requestPasswordRecovery", async (_e, email: string) => {
    await sessionService.requestPasswordRecovery(email);
  });
  ipcMain.handle("session:getReferralCode", async () => (
    sessionService.getReferralCode()
  ));
  ipcMain.handle("session:logout", () => {
    profileSync.stop();
    progressSync.stop();
    catalogRealtimeSync.stop();
    sessionService.logout();
  });
  ipcMain.handle("session:updateProfile", async (_e, displayName: string) => {
    const result = await sessionService.updateProfile(displayName);
    return { ...sessionService.getSnapshot(), ...result };
  });
  ipcMain.handle(
    "session:changePassword",
    async (_e, currentPassword: string, newPassword: string) => {
      await sessionService.changePassword(currentPassword, newPassword);
      return sessionService.getSnapshot();
    },
  );
  ipcMain.handle("session:uploadAvatar", async (_e, jpegBytes: Uint8Array | number[]) => {
    coerceAvatarJpegBytes(jpegBytes);
    await sessionService.uploadAvatar(jpegBytes);
    return sessionService.getSnapshot();
  });
}
