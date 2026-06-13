import { describe, expect, it, vi } from "vitest";
import { upsertUserProfileMirror } from "../src/shared/userProfileMirror.js";

describe("upsertUserProfileMirror", () => {
  it("posts merge-duplicates upsert to user_profiles", async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);

    await upsertUserProfileMirror(
      { baseUrl: "http://localhost:8000", anonKey: "anon-key" },
      "access-token",
      {
        user_id: "user-1",
        display_name: "Alice",
        avatar_url: "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg",
        updated_at: "2026-06-13T12:00:00.000Z",
      },
    );

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8000/rest/v1/user_profiles?on_conflict=user_id");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({
      apikey: "anon-key",
      Authorization: "Bearer access-token",
      Prefer: "resolution=merge-duplicates,return=minimal",
    });
    expect(JSON.parse(String(init.body))).toEqual({
      user_id: "user-1",
      display_name: "Alice",
      avatar_url: "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg",
      updated_at: "2026-06-13T12:00:00.000Z",
    });

    vi.unstubAllGlobals();
  });
});
