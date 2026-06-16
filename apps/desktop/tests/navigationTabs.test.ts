import { describe, expect, it } from "vitest";
import {
  BOTTOM_NAV_TABS,
  LIBRARY_TOP_TABS,
} from "../src/shared/navigation.js";

describe("desktop navigation tabs", () => {
  it("matches the Android bottom navigation order for downloads", () => {
    expect(BOTTOM_NAV_TABS).toEqual(["library", "downloads", "profile"]);
  });

  it("keeps downloads out of the library top tabs", () => {
    expect(LIBRARY_TOP_TABS).toEqual(["audiobooks", "music"]);
  });
});
