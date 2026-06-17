import { describe, expect, it } from "vitest";
import {
  libraryScrollPaddingTop,
  LIBRARY_TOP_SCROLL_BOOKS_PX,
  LIBRARY_TOP_SCROLL_MUSIC_PX,
  LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX,
  scrollPadBottomCss,
} from "../src/renderer/src/lib/layoutChrome.js";

describe("layoutChrome", () => {
  it("computes library top scroll padding", () => {
    expect(libraryScrollPaddingTop("music", false)).toBe(LIBRARY_TOP_SCROLL_MUSIC_PX);
    expect(libraryScrollPaddingTop("books", false)).toBe(LIBRARY_TOP_SCROLL_BOOKS_PX);
    expect(libraryScrollPaddingTop("books", true)).toBe(
      LIBRARY_TOP_SCROLL_BOOKS_PX + LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX,
    );
    expect(libraryScrollPaddingTop("music", true)).toBe(
      LIBRARY_TOP_SCROLL_MUSIC_PX + LIBRARY_TOP_SCROLL_OFFLINE_EXTRA_PX,
    );
  });

  it("computes bottom scroll padding css", () => {
    expect(scrollPadBottomCss(false, true)).toBe("calc(5.5rem + 1rem)");
    expect(scrollPadBottomCss(true, true)).toBe("calc(10.5rem + 1rem)");
    expect(scrollPadBottomCss(true, false)).toBe("calc(6rem + 1rem)");
  });
});
