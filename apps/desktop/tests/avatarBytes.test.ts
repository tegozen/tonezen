import { describe, expect, it } from "vitest";
import { avatarUrlWithCacheBust, coerceAvatarJpegBytes } from "../src/shared/avatarBytes.js";

describe("coerceAvatarJpegBytes", () => {
  it("accepts Uint8Array", () => {
    const bytes = new Uint8Array([1, 2, 3]);
    expect(coerceAvatarJpegBytes(bytes)).toBe(bytes);
  });

  it("accepts number arrays from IPC", () => {
    expect(Array.from(coerceAvatarJpegBytes([4, 5, 6]))).toEqual([4, 5, 6]);
  });

  it("accepts indexed objects from IPC", () => {
    expect(Array.from(coerceAvatarJpegBytes({ 0: 7, 1: 8, 2: 9 }))).toEqual([7, 8, 9]);
  });
});

describe("avatarUrlWithCacheBust", () => {
  it("appends cache-bust query param", () => {
    expect(avatarUrlWithCacheBust("http://localhost/a.jpg")).toMatch(/\?t=\d+$/);
  });
});
