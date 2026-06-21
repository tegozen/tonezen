import { describe, expect, it } from "vitest";
import { maxUpdatedAt } from "../src/db/indexerState.js";

describe("maxUpdatedAt", () => {
  it("returns the latest updatedAt from objects", () => {
    const latest = new Date("2025-06-02T00:00:00.000Z");
    const result = maxUpdatedAt([
      { updatedAt: new Date("2025-06-01T00:00:00.000Z") },
      { updatedAt: latest },
      { updatedAt: null },
    ]);

    expect(result.toISOString()).toBe(latest.toISOString());
  });

  it("prefers catalogUpdatedAt when display-name mapping changed later", () => {
    const result = maxUpdatedAt([
      {
        updatedAt: new Date("2025-06-01T00:00:00.000Z"),
        catalogUpdatedAt: new Date("2025-06-03T00:00:00.000Z"),
      },
      { updatedAt: new Date("2025-06-02T00:00:00.000Z") },
    ]);

    expect(result.toISOString()).toBe("2025-06-03T00:00:00.000Z");
  });

  it("returns now when no object timestamps exist", () => {
    const before = Date.now();
    const result = maxUpdatedAt([{ updatedAt: null }]);
    const after = Date.now();

    expect(result.getTime()).toBeGreaterThanOrEqual(before);
    expect(result.getTime()).toBeLessThanOrEqual(after);
  });
});
