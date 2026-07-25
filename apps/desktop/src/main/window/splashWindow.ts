import fs from "node:fs";
import { BrowserWindow } from "electron";
import { appIconPath, splashImagePath } from "../app/assets.js";

export function createSplashWindow(): BrowserWindow {
  const splashWindow = new BrowserWindow({
    width: 520,
    height: 320,
    frame: false,
    resizable: false,
    show: false,
    backgroundColor: "#020617",
    icon: appIconPath,
    webPreferences: {
      sandbox: true,
    },
  });

  const splashDataUrl = `data:image/png;base64,${fs.readFileSync(splashImagePath).toString("base64")}`;
  const html = `<!doctype html>
<html lang="ru">
  <head>
    <meta charset="utf-8" />
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline';" />
    <style>
      html,
      body {
        margin: 0;
        width: 100%;
        height: 100%;
        overflow: hidden;
        background: #020617;
      }

      body {
        display: grid;
        place-items: center;
      }

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        user-select: none;
        -webkit-user-drag: none;
      }
    </style>
  </head>
  <body>
    <img src="${splashDataUrl}" alt="" />
  </body>
</html>`;

  void splashWindow.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(html)}`);
  splashWindow.once("ready-to-show", () => splashWindow.show());

  return splashWindow;
}

export function closeSplashWindow(splashWindow: BrowserWindow | null): void {
  if (splashWindow && !splashWindow.isDestroyed()) {
    splashWindow.close();
  }
}
