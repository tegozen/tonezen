import { describe, expect, it } from "vitest";
import {
  MEDIA_HAVE_METADATA,
  needsMetadataBeforeSeek,
  startSecondsFromMs,
} from "../src/shared/playbackStart.js";

describe("needsMetadataBeforeSeek", () => {
  it("waits when resuming before metadata is ready", () => {
    expect(needsMetadataBeforeSeek(475_000, 0)).toBe(true);
    expect(needsMetadataBeforeSeek(475_000, MEDIA_HAVE_METADATA - 1)).toBe(true);
  });

  it("seeks immediately when metadata is already loaded", () => {
    expect(needsMetadataBeforeSeek(475_000, MEDIA_HAVE_METADATA)).toBe(false);
    expect(needsMetadataBeforeSeek(0, 0)).toBe(false);
  });
});

describe("startSecondsFromMs", () => {
  it("converts milliseconds to seconds", () => {
    expect(startSecondsFromMs(475_000)).toBe(475);
  });
});
