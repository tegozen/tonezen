import { describe, expect, it } from "vitest";
import { BOTTOM_NAV_TABS } from "../src/shared/navigation.js";

describe("desktop navigation tabs", () => {
  it("matches the Android bottom navigation order for media sections", () => {
    expect(BOTTOM_NAV_TABS).toEqual(["music", "books", "downloads", "profile"]);
  });
});
