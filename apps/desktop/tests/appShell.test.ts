import { describe, expect, it } from "vitest";
import {
  APP_SHELL_DEFAULT_HEIGHT_PX,
  APP_SHELL_WIDTH_PX,
  mainWindowContentSize,
} from "../src/shared/appShell.js";

describe("appShell", () => {
  it("matches renderer app-frame max width", () => {
    expect(APP_SHELL_WIDTH_PX).toBe(430);
    expect(mainWindowContentSize()).toEqual({
      width: APP_SHELL_WIDTH_PX,
      height: APP_SHELL_DEFAULT_HEIGHT_PX,
    });
  });
});
