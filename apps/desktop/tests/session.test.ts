import { describe, expect, it } from "vitest";
import { mergeProgressLww, SessionManager } from "../src/shared/session.js";

describe("SessionManager", () => {
  const session = {
    userId: "u1",
    accessToken: "a",
    refreshToken: "r",
    expiresAtEpochSeconds: 1000,
  };

  it("keeps authenticated offline when jwt expired", () => {
    const mgr = new SessionManager(300, () => 2000);
    expect(mgr.resolveState(session, false)).toBe("AuthenticatedOffline");
    expect(mgr.shouldRefresh(session, false)).toBe(false);
  });

  it("marks stale when expired online", () => {
    const mgr = new SessionManager(300, () => 2000);
    expect(mgr.resolveState(session, true)).toBe("AuthenticatedStale");
    expect(mgr.shouldRefresh(session, true)).toBe(true);
  });
});

describe("mergeProgressLww", () => {
  it("prefers newer timestamp", () => {
    const older = {
      bookId: "b1",
      trackId: "t1",
      positionMs: 1,
      updatedAt: "2024-01-01T00:00:00Z",
    };
    const newer = {
      bookId: "b1",
      trackId: "t2",
      positionMs: 2,
      updatedAt: "2024-06-01T00:00:00Z",
    };
    expect(mergeProgressLww(older, newer)).toEqual(newer);
  });
});
