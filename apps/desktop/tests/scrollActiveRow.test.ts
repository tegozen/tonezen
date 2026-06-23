import { describe, expect, it } from "vitest";
import { scrollActiveRowAboveBottomPadding } from "../src/renderer/src/lib/scrollActiveRow.js";

function mockScrollContainer({
  clientHeight,
  scrollTop,
  paddingBottom,
}: {
  clientHeight: number;
  scrollTop: number;
  paddingBottom: number;
}) {
  let top = scrollTop;
  const scrollEl = {
    clientHeight,
    get scrollTop() {
      return top;
    },
    scrollTo({ top: nextTop, behavior }: { top: number; behavior?: ScrollBehavior }) {
      top = Math.max(0, nextTop);
      void behavior;
    },
  } as unknown as HTMLElement;

  Object.defineProperty(scrollEl, "scrollTop", {
    get: () => top,
    set: (value: number) => {
      top = value;
    },
    configurable: true,
  });

  const originalGetComputedStyle = globalThis.getComputedStyle;
  globalThis.getComputedStyle = ((el: Element) => {
    if (el === scrollEl) {
      return { paddingBottom: `${paddingBottom}px` } as CSSStyleDeclaration;
    }
    return originalGetComputedStyle(el);
  }) as typeof getComputedStyle;

  return {
    scrollEl,
    getScrollTop: () => top,
    restore() {
      globalThis.getComputedStyle = originalGetComputedStyle;
    },
  };
}

describe("scrollActiveRowAboveBottomPadding", () => {
  it("scrolls a row below the mini-player reserve into view", () => {
    const { scrollEl, getScrollTop, restore } = mockScrollContainer({
      clientHeight: 600,
      scrollTop: 0,
      paddingBottom: 112,
    });
    const rowEl = { offsetTop: 900, offsetHeight: 56 } as HTMLElement;

    scrollActiveRowAboveBottomPadding(scrollEl, rowEl, "auto");

    expect(getScrollTop()).toBe(900 + 56 - (600 - 112));
    restore();
  });

  it("does not scroll when the row is already fully visible", () => {
    const { scrollEl, getScrollTop, restore } = mockScrollContainer({
      clientHeight: 600,
      scrollTop: 200,
      paddingBottom: 112,
    });
    const rowEl = { offsetTop: 300, offsetHeight: 56 } as HTMLElement;

    scrollActiveRowAboveBottomPadding(scrollEl, rowEl, "auto");

    expect(getScrollTop()).toBe(200);
    restore();
  });

  it("scrolls upward when the row is above the viewport", () => {
    const { scrollEl, getScrollTop, restore } = mockScrollContainer({
      clientHeight: 600,
      scrollTop: 500,
      paddingBottom: 112,
    });
    const rowEl = { offsetTop: 120, offsetHeight: 56 } as HTMLElement;

    scrollActiveRowAboveBottomPadding(scrollEl, rowEl, "auto");

    expect(getScrollTop()).toBe(120);
    restore();
  });
});
