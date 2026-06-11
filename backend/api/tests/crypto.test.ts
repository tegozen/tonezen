import { describe, expect, it } from "vitest";
import { mergeProgressLww, signDownloadUrl } from "../src/lib/crypto.js";

describe("signDownloadUrl", () => {
  it("generates url with md5 and expires params", () => {
    const url = signDownloadUrl(
      "cycles/test/audio/01.mp3",
      "test-secret-key-min-32-characters!!",
      900,
      "http://localhost:8080",
      1000000000,
    );
    expect(url).toContain("http://localhost:8080/download/cycles/test/audio/01.mp3");
    expect(url).toContain("md5=");
    expect(url).toContain("expires=1000000900");
  });
});

describe("mergeProgressLww", () => {
  const older = {
    book_id: "b1",
    track_id: "t1",
    position_ms: 1000,
    updated_at: "2024-01-01T00:00:00Z",
  };
  const newer = {
    book_id: "b1",
    track_id: "t2",
    position_ms: 2000,
    updated_at: "2024-06-01T00:00:00Z",
  };

  it("returns newer record on conflict", () => {
    expect(mergeProgressLww(older, newer)).toEqual(newer);
    expect(mergeProgressLww(newer, older)).toEqual(newer);
  });

  it("returns single side when other is null", () => {
    expect(mergeProgressLww(older, null)).toEqual(older);
    expect(mergeProgressLww(null, newer)).toEqual(newer);
  });
});
