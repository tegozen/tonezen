import { describe, expect, it, vi } from "vitest";
import { AppUiReferences } from "../src/main/appUiReferences.js";

type Destroyable = {
  on: (event: string, callback: () => void) => void;
};

function destroyable(): Destroyable & { destroy: () => void } {
  let onClosed: (() => void) | null = null;
  return {
    on(event, callback) {
      if (event === "closed") onClosed = callback;
    },
    destroy() {
      onClosed?.();
    },
  };
}

describe("AppUiReferences", () => {
  it("keeps Electron UI objects reachable until they are closed", () => {
    const refs = new AppUiReferences();
    const splash = destroyable();
    const mainWindow = destroyable();
    const tray = { destroy: vi.fn() };

    refs.setSplashWindow(splash);
    refs.setMainWindow(mainWindow);
    refs.setTray(tray);

    expect(refs.current()).toEqual({ splashWindow: splash, mainWindow, tray });

    splash.destroy();
    mainWindow.destroy();

    expect(refs.current()).toEqual({ splashWindow: null, mainWindow: null, tray });
  });

  it("restores the retained main window for a subsequent app launch", () => {
    const refs = new AppUiReferences();
    const mainWindow = {
      ...destroyable(),
      focus: vi.fn(),
      isDestroyed: vi.fn(() => false),
      isMinimized: vi.fn(() => true),
      restore: vi.fn(),
      show: vi.fn(),
    };

    refs.setMainWindow(mainWindow);

    expect(refs.showMainWindow()).toBe(true);
    expect(mainWindow.restore).toHaveBeenCalledOnce();
    expect(mainWindow.show).toHaveBeenCalledOnce();
    expect(mainWindow.focus).toHaveBeenCalledOnce();

    mainWindow.destroy();

    expect(refs.showMainWindow()).toBe(false);
    expect(mainWindow.show).toHaveBeenCalledOnce();
  });
});
