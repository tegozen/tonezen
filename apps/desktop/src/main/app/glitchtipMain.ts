import * as Sentry from "@sentry/electron/main";
import { app } from "electron";

/** Init Sentry → GlitchTip as early as possible (native crashReporter). */
export function initGlitchtipMain(dsn: string): void {
  const trimmed = dsn.trim();
  if (!trimmed) return;

  Sentry.init({
    dsn: trimmed,
    sendDefaultPii: false,
    environment: app.isPackaged ? "release" : "debug",
    release: `tonezen-desktop@${app.getVersion()}`,
  });
}
