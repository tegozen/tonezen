import { describe, expect, it } from "vitest";
import { parseUpdatedSince } from "../src/lib/queryParams.js";

describe("parseUpdatedSince", () => {
  it("returns undefined when query param is omitted", () => {
    expect(parseUpdatedSince({})).toBeUndefined();
  });

  it("returns ISO timestamp when valid", () => {
    expect(parseUpdatedSince({ updated_since: "2024-01-01T00:00:00Z" })).toBe(
      "2024-01-01T00:00:00Z",
    );
  });

  it("returns false when present but invalid", () => {
    expect(parseUpdatedSince({ updated_since: "not-a-date" })).toBe(false);
    expect(parseUpdatedSince({ updated_since: 123 })).toBe(false);
  });
});
