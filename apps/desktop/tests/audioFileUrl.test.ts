import { describe, expect, it } from "vitest";
import {
  localAudioPathFromUrl,
  toAudioFileUrl,
} from "../src/shared/localAudioUrl.js";

describe("toAudioFileUrl", () => {
  it("builds tonezen-audio URLs for Windows drive paths", () => {
    expect(toAudioFileUrl("D:\\repo\\tonezen\\track.mp3")).toBe(
      "tonezen-audio://local/D%3A%2Frepo%2Ftonezen%2Ftrack.mp3",
    );
  });

  it("encodes spaces in paths", () => {
    expect(toAudioFileUrl("D:/music/my track.mp3")).toBe(
      "tonezen-audio://local/D%3A%2Fmusic%2Fmy%20track.mp3",
    );
  });

  it("round-trips through localAudioPathFromUrl", () => {
    const url = toAudioFileUrl("D:/music/track.mp3");
    expect(localAudioPathFromUrl(url)).toBe("D:/music/track.mp3");
  });
});

describe("localAudioPathFromUrl", () => {
  it("returns null for unrelated URLs", () => {
    expect(localAudioPathFromUrl("file:///D:/track.mp3")).toBeNull();
  });
});
