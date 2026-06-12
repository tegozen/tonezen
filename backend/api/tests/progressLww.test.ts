import { describe, expect, it } from "vitest";
import { mergeProgressLww, maybeSkipProgressWrite } from "../src/lib/progressLww.js";

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

describe("maybeSkipProgressWrite", () => {
  const incoming = {
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

  it("skips when existing record wins", () => {
    expect(maybeSkipProgressWrite(incoming, newer)).toEqual({
      skipped: true,
      progress: newer,
    });
  });

  it("returns null when incoming wins or no existing record", () => {
    expect(maybeSkipProgressWrite(newer, incoming)).toBeNull();
    expect(maybeSkipProgressWrite(incoming, null)).toBeNull();
    expect(maybeSkipProgressWrite(incoming, undefined)).toBeNull();
  });
});
