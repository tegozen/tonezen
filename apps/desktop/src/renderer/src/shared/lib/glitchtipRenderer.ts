import * as Sentry from "@sentry/electron/renderer";

/** Renderer-side Sentry bridge (errors flow via main). */
export function initGlitchtipRenderer(): void {
  Sentry.init({
    sendDefaultPii: false,
  });
}
