import { describe, expect, it } from "vitest";
import { metadataFromStoredIfUnchanged } from "../src/mediaProbe.js";

describe("metadataFromStoredIfUnchanged", () => {
  it("reuses stored metadata when file size is unchanged", () => {
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000 },
        1024,
      ),
    ).toEqual({
      sizeBytes: 1024,
      checksum: "abc",
      durationMs: 60000,
    });
  });

  it("returns null when checksum is missing or size changed", () => {
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: null, size_bytes: 1024, duration_ms: 60000 },
        1024,
      ),
    ).toBeNull();
    expect(
      metadataFromStoredIfUnchanged(
        { checksum: "abc", size_bytes: 1024, duration_ms: 60000 },
        2048,
      ),
    ).toBeNull();
  });
});
