import { describe, expect, it, vi, afterEach } from "vitest";
import {
  SupabaseAuthClient,
  displayNameFromUser,
  mergeSessionOnRefresh,
  sessionFromGoTrue,
} from "../src/shared/supabaseAuth.js";

describe("SupabaseAuthClient", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("signs in with password", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          access_token: "at",
          refresh_token: "rt",
          expires_in: 3600,
          token_type: "bearer",
          user: {
            id: "user-1",
            email: "a@b.c",
            user_metadata: { full_name: "Alex Mercer" },
          },
        }),
      }),
    );

    const client = new SupabaseAuthClient({
      baseUrl: "http://localhost:8000",
      anonKey: "anon-key",
    });
    const result = await client.signInWithPassword("a@b.c", "secret");
    expect(result.access_token).toBe("at");
    const session = sessionFromGoTrue(result);
    expect(session.userId).toBe("user-1");
    expect(session.email).toBe("a@b.c");
    expect(session.displayName).toBe("Alex Mercer");
  });

  it("falls back to email local part when display name is missing", () => {
    expect(displayNameFromUser({ id: "u1", email: "admin@tonezen.local" })).toBe("Admin");
  });

  it("maps avatar and member since from GoTrue user", () => {
    const session = sessionFromGoTrue({
      access_token: "at",
      refresh_token: "rt",
      expires_in: 3600,
      token_type: "bearer",
      user: {
        id: "user-1",
        email: "admin@tonezen.local",
        created_at: "2026-06-12T00:00:00.000Z",
        user_metadata: {
          full_name: "Admin",
          avatar_url: "https://example.com/avatar.jpg",
        },
      },
    });
    expect(session.avatarUrl).toBe("https://example.com/avatar.jpg");
    expect(session.memberSinceEpochMs).toBe(Date.parse("2026-06-12T00:00:00.000Z"));
  });

  it("mergeSessionOnRefresh preserves avatarUrl when refreshed metadata omits it", () => {
    const previous = sessionFromGoTrue({
      access_token: "at-old",
      refresh_token: "rt-old",
      expires_in: 3600,
      token_type: "bearer",
      user: {
        id: "user-1",
        email: "admin@tonezen.local",
        user_metadata: {
          avatar_url: "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg",
        },
      },
    });
    const refreshed = sessionFromGoTrue({
      access_token: "at-new",
      refresh_token: "rt-new",
      expires_in: 3600,
      token_type: "bearer",
      user: {
        id: "user-1",
        email: "admin@tonezen.local",
        user_metadata: { full_name: "Admin" },
      },
    });

    const merged = mergeSessionOnRefresh(previous, refreshed);

    expect(merged.accessToken).toBe("at-new");
    expect(merged.avatarUrl).toBe(previous.avatarUrl);
  });
});
