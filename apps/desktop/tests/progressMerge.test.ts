import { describe, expect, it } from "vitest";
import { mergeProgressLww } from "../src/shared/progressMerge.js";

describe("mergeProgressLww", () => {
  const older = {
    bookId: "b1",
    trackId: "t1",
    positionMs: 1000,
    updatedAt: "2024-01-01T00:00:00Z",
  };
  const newer = {
    bookId: "b1",
    trackId: "t2",
    positionMs: 2000,
    updatedAt: "2024-06-01T00:00:00Z",
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
