import { describe, expect, it } from "vitest";
import {
  APP_SHELL_DEFAULT_HEIGHT_PX,
  APP_SHELL_MIN_HEIGHT_PX,
  APP_SHELL_WIDTH_PX,
  mainWindowContentSize,
  needsMainWindowWidthEnforcement,
  normalizeMainWindowContentSize,
} from "../src/shared/appShell.js";

describe("appShell", () => {
  it("matches renderer app-frame max width", () => {
    expect(APP_SHELL_WIDTH_PX).toBe(430);
    expect(mainWindowContentSize()).toEqual({
      width: APP_SHELL_WIDTH_PX,
      height: APP_SHELL_DEFAULT_HEIGHT_PX,
    });
  });

  it("detects when shell width drifted", () => {
    expect(needsMainWindowWidthEnforcement(430)).toBe(false);
    expect(needsMainWindowWidthEnforcement(900)).toBe(true);
  });

  it("normalizes content width while preserving height", () => {
    expect(normalizeMainWindowContentSize(720)).toEqual({
      width: APP_SHELL_WIDTH_PX,
      height: 720,
    });
    expect(normalizeMainWindowContentSize(400)).toEqual({
      width: APP_SHELL_WIDTH_PX,
      height: APP_SHELL_MIN_HEIGHT_PX,
    });
  });
});
