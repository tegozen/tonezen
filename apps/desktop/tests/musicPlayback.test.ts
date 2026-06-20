import { describe, expect, it } from "vitest";
import { nextMusicIndex } from "../src/shared/musicList.js";
import {
  findActiveMusicTrack,
  findFirstPlayableMusicTrack,
  findNextPlayableIndex,
  findPreviousPlayableIndex,
  isMusicTrackPlayable,
  shouldRestartCurrentMusicTrack,
} from "../src/shared/musicPlayback.js";

describe("shouldRestartCurrentMusicTrack", () => {
  it("restarts when position is beyond threshold", () => {
    expect(shouldRestartCurrentMusicTrack(3001)).toBe(true);
  });

  it("skips to previous track near start", () => {
    expect(shouldRestartCurrentMusicTrack(3000)).toBe(false);
  });
});

describe("music playback advance", () => {
  const tracks = [
    { trackId: "a", isDownloaded: true },
    { trackId: "b", isDownloaded: false },
    { trackId: "c", isDownloaded: true },
  ];

  it("finds next downloaded track when offline", () => {
    const isPlayable = (track: { isDownloaded: boolean }) =>
      isMusicTrackPlayable(track, "AuthenticatedOffline");
    expect(findNextPlayableIndex(tracks, 0, isPlayable, nextMusicIndex)).toBe(2);
  });

  it("returns null when no playable track remains", () => {
    const offlineTracks = [
      { trackId: "a", isDownloaded: true },
      { trackId: "b", isDownloaded: false },
    ];
    const isPlayable = (track: { isDownloaded: boolean }) =>
      isMusicTrackPlayable(track, "AuthenticatedOffline");
    expect(findNextPlayableIndex(offlineTracks, 0, isPlayable, nextMusicIndex)).toBeNull();
  });

  it("finds previous downloaded track when offline", () => {
    const isPlayable = (track: { isDownloaded: boolean }) =>
      isMusicTrackPlayable(track, "AuthenticatedOffline");
    expect(findPreviousPlayableIndex(tracks, 0, isPlayable, (current, size) => (current - 1 + size) % size)).toBe(2);
  });

  it("finds the first playable wave track", () => {
    const waveTracks = [
      { trackId: "a", isDownloaded: false },
      { trackId: "b", isDownloaded: true },
      { trackId: "c", isDownloaded: true },
    ];
    expect(findFirstPlayableMusicTrack(waveTracks, "AuthenticatedOffline")?.trackId).toBe("b");
  });

  it("returns no wave track offline when nothing is downloaded", () => {
    const waveTracks = [
      { trackId: "a", isDownloaded: false },
      { trackId: "b", isDownloaded: false },
    ];
    expect(findFirstPlayableMusicTrack(waveTracks, "AuthenticatedOffline")).toBeNull();
  });
});

describe("findActiveMusicTrack", () => {
  const fullQueue = Array.from({ length: 30 }, (_, index) => ({
    trackId: `t${index}`,
    isDownloaded: true,
  }));

  it("falls back to the full queue when the visible window does not contain the active track", () => {
    const visibleWindow = fullQueue.slice(0, 24);
    expect(findActiveMusicTrack(visibleWindow, fullQueue, "t27")?.trackId).toBe("t27");
  });
});
