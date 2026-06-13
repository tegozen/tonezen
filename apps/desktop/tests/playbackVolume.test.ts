import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import {
  clampPlaybackVolume,
  loadPlaybackVolume,
  savePlaybackVolume,
} from "../src/renderer/src/lib/playbackVolume.js";

describe("playbackVolume", () => {
  it("clamps volume to 0..1", () => {
    expect(clampPlaybackVolume(-0.5)).toBe(0);
    expect(clampPlaybackVolume(0.42)).toBe(0.42);
    expect(clampPlaybackVolume(1.5)).toBe(1);
    expect(clampPlaybackVolume(Number.NaN)).toBe(1);
  });

  describe("persistence", () => {
    const storage = new Map<string, string>();

    beforeEach(() => {
      storage.clear();
      vi.stubGlobal("localStorage", {
        getItem: (key: string) => storage.get(key) ?? null,
        setItem: (key: string, value: string) => {
          storage.set(key, value);
        },
      });
    });

    afterEach(() => {
      vi.unstubAllGlobals();
    });

    it("loads default volume when storage is empty", () => {
      expect(loadPlaybackVolume()).toBe(1);
    });

    it("persists clamped volume", () => {
      savePlaybackVolume(0.33);
      expect(loadPlaybackVolume()).toBe(0.33);
      savePlaybackVolume(2);
      expect(loadPlaybackVolume()).toBe(1);
    });
  });
});
