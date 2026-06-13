import { describe, expect, it } from "vitest";
import { seekFractionFromPointer } from "../src/shared/playbackSeek.js";

describe("seekFractionFromPointer", () => {
  it("maps pointer position to 0..1 fraction", () => {
    expect(seekFractionFromPointer(50, 0, 100)).toBe(0.5);
    expect(seekFractionFromPointer(0, 0, 100)).toBe(0);
    expect(seekFractionFromPointer(100, 0, 100)).toBe(1);
  });

  it("clamps out-of-range values", () => {
    expect(seekFractionFromPointer(-10, 0, 100)).toBe(0);
    expect(seekFractionFromPointer(150, 0, 100)).toBe(1);
  });

  it("returns zero for invalid bar width", () => {
    expect(seekFractionFromPointer(10, 0, 0)).toBe(0);
    expect(seekFractionFromPointer(10, 0, -5)).toBe(0);
  });
});
