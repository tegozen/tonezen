import { describe, expect, it } from "vitest";
import { SessionManager } from "../src/shared/session.js";

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
