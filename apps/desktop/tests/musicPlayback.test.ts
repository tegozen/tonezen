import { describe, expect, it } from "vitest";
import { nextMusicIndex } from "../src/shared/musicList.js";
import {
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
});
