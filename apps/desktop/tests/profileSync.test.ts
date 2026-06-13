import { describe, expect, it } from "vitest";
import { resolveSyncedAvatarUrl, stripAvatarQuery } from "../src/shared/profileSync.js";

describe("profileSync", () => {
  it("strips cache-bust query from avatar url", () => {
    expect(stripAvatarQuery("http://localhost/a.jpg?t=1")).toBe("http://localhost/a.jpg");
  });

  it("cache-busts avatar when profile updated_at changed", () => {
    const url = resolveSyncedAvatarUrl({
      prevAvatarUrl: "http://localhost/a.jpg?t=1",
      prevProfileUpdatedAt: "2026-06-13T10:00:00Z",
      nextAvatarBase: "http://localhost/a.jpg",
      serverUpdatedAt: "2026-06-13T11:00:00Z",
      bust: (value) => `${value}?t=new`,
    });
    expect(url).toBe("http://localhost/a.jpg?t=new");
  });

  it("keeps cached avatar url when profile revision is unchanged", () => {
    const url = resolveSyncedAvatarUrl({
      prevAvatarUrl: "http://localhost/a.jpg?t=old",
      prevProfileUpdatedAt: "2026-06-13T10:00:00Z",
      nextAvatarBase: "http://localhost/a.jpg",
      serverUpdatedAt: "2026-06-13T10:00:00Z",
      bust: (value) => `${value}?t=new`,
    });
    expect(url).toBe("http://localhost/a.jpg?t=old");
  });
});
