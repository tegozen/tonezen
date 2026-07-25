export type RetainedWindow = {
  focus?(): unknown;
  isMinimized?(): boolean;
  on(event: "closed", listener: () => void): unknown;
  isDestroyed?(): boolean;
  restore?(): unknown;
  show?(): unknown;
};

export type RetainedTray = object;

export class AppUiReferences {
  private splashWindow: RetainedWindow | null = null;
  private mainWindow: RetainedWindow | null = null;
  private tray: RetainedTray | null = null;

  setSplashWindow(window: RetainedWindow | null): void {
    this.splashWindow = window;
    window?.on("closed", () => {
      if (this.splashWindow === window) this.splashWindow = null;
    });
  }

  setMainWindow(window: RetainedWindow | null): void {
    this.mainWindow = window;
    window?.on("closed", () => {
      if (this.mainWindow === window) this.mainWindow = null;
    });
  }

  setTray(tray: RetainedTray | null): void {
    this.tray = tray;
  }

  showMainWindow(): boolean {
    const mainWindow = this.mainWindow;
    if (!mainWindow || mainWindow.isDestroyed?.() || !mainWindow.show) return false;

    if (mainWindow.isMinimized?.()) mainWindow.restore?.();
    mainWindow.show();
    mainWindow.focus?.();
    return true;
  }

  current(): {
    splashWindow: RetainedWindow | null;
    mainWindow: RetainedWindow | null;
    tray: RetainedTray | null;
  } {
    return {
      splashWindow: this.splashWindow,
      mainWindow: this.mainWindow,
      tray: this.tray,
    };
  }
}
