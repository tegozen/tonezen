import { describe, expect, it } from "vitest";
import {
  libraryScrollPaddingTop,
  LIBRARY_TOP_SCROLL_AUDIOBOOKS_PX,
  LIBRARY_TOP_SCROLL_MUSIC_PX,
  LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX,
  scrollPadBottomCss,
} from "../src/renderer/src/lib/layoutChrome.js";

describe("layoutChrome", () => {
  it("computes library top scroll padding", () => {
    expect(libraryScrollPaddingTop(false, false)).toBe(LIBRARY_TOP_SCROLL_MUSIC_PX);
    expect(libraryScrollPaddingTop(true, false)).toBe(LIBRARY_TOP_SCROLL_AUDIOBOOKS_PX);
    expect(libraryScrollPaddingTop(true, true)).toBe(
      LIBRARY_TOP_SCROLL_AUDIOBOOKS_PX + LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX,
    );
  });

  it("computes bottom scroll padding css", () => {
    expect(scrollPadBottomCss(false, true)).toBe("calc(5.5rem + 1rem)");
    expect(scrollPadBottomCss(true, true)).toBe("calc(10.5rem + 1rem)");
    expect(scrollPadBottomCss(true, false)).toBe("calc(6rem + 1rem)");
  });
});
