import { describe, expect, it } from "vitest";
import { isAuthSubscriptionError } from "../src/main/catalogRealtimeSync.js";

describe("isAuthSubscriptionError", () => {
  it("detects expired JWT realtime errors", () => {
    const err = new Error("Realtime error", {
      cause: { reason: "Token has expired 143 seconds ago" },
    });
    expect(isAuthSubscriptionError(err)).toBe(true);
  });

  it("ignores unrelated channel errors", () => {
    expect(isAuthSubscriptionError(new Error("connection closed"))).toBe(false);
  });
});
