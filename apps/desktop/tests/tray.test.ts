import { describe, expect, it, vi } from "vitest";
import { handleTrayIconClick } from "../src/main/tray.js";

describe("handleTrayIconClick", () => {
  it("opens the tray menu on macOS instead of toggling the window", () => {
    const showWindow = vi.fn();
    const hideWindow = vi.fn();
    const popUpMenu = vi.fn();

    handleTrayIconClick({
      platform: "darwin",
      isWindowVisible: false,
      showWindow,
      hideWindow,
      popUpMenu,
    });

    expect(popUpMenu).toHaveBeenCalledOnce();
    expect(showWindow).not.toHaveBeenCalled();
    expect(hideWindow).not.toHaveBeenCalled();
  });

  it("shows the window on Windows when hidden", () => {
    const showWindow = vi.fn();
    const hideWindow = vi.fn();
    const popUpMenu = vi.fn();

    handleTrayIconClick({
      platform: "win32",
      isWindowVisible: false,
      showWindow,
      hideWindow,
      popUpMenu,
    });

    expect(showWindow).toHaveBeenCalledOnce();
    expect(hideWindow).not.toHaveBeenCalled();
    expect(popUpMenu).not.toHaveBeenCalled();
  });

  it("hides the window on Windows when visible", () => {
    const showWindow = vi.fn();
    const hideWindow = vi.fn();
    const popUpMenu = vi.fn();

    handleTrayIconClick({
      platform: "win32",
      isWindowVisible: true,
      showWindow,
      hideWindow,
      popUpMenu,
    });

    expect(hideWindow).toHaveBeenCalledOnce();
    expect(showWindow).not.toHaveBeenCalled();
    expect(popUpMenu).not.toHaveBeenCalled();
  });
});
