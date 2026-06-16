import { describe, expect, it } from "vitest";
import { computeBulkDownloaded, mergePriority, shouldUpgrade, sortPending } from "../src/shared/downloadQueuePolicy.js";

describe("downloadQueuePolicy", () => {
  it("sorts by priority then enqueue time", () => {
    const sorted = sortPending([
      { key: { bookId: "b1", trackId: "t2" }, priority: "BULK", enqueuedAt: 2 },
      { key: { bookId: "b1", trackId: "t1" }, priority: "PLAY", enqueuedAt: 3 },
      { key: { bookId: "b1", trackId: "t3" }, priority: "USER", enqueuedAt: 1 },
    ]);
    expect(sorted.map((item) => item.key.trackId)).toEqual(["t1", "t3", "t2"]);
  });

  it("mergePriority keeps higher weight", () => {
    expect(mergePriority("USER", "PLAY")).toBe("PLAY");
    expect(mergePriority("BULK", "PREFETCH")).toBe("BULK");
  });

  it("shouldUpgrade only when incoming is higher", () => {
    expect(shouldUpgrade("PREFETCH", "USER")).toBe(true);
    expect(shouldUpgrade("PLAY", "BULK")).toBe(false);
  });

  it("computeBulkDownloaded includes skipped-at-enqueue tracks", () => {
    const batchId = "batch-1";
    expect(
      computeBulkDownloaded(3, batchId, [
        { batchId, trackId: "t1" },
        { batchId, trackId: "t2" },
      ] as Array<{ batchId: string; trackId: string }>),
    ).toBe(5);
    expect(computeBulkDownloaded(0, null, [])).toBe(0);
  });
});
