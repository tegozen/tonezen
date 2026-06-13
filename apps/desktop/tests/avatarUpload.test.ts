import { describe, expect, it } from "vitest";
import { normalizeAvatarUrl, publicAvatarUrl, uploadAvatarToStorage } from "../src/shared/avatarUpload.js";

describe("normalizeAvatarUrl", () => {
  it("rewrites emulator host to client base URL", () => {
    const userId = "3957cba3-e20f-47f6-bd74-f4c5beaf7d08";
    expect(
      normalizeAvatarUrl(
        `http://10.0.2.2:8000/storage/v1/object/public/avatars/${userId}/avatar.jpg`,
        "http://localhost:8000",
      ),
    ).toBe(publicAvatarUrl("http://localhost:8000", userId));
  });

  it("preserves non-storage avatar URLs", () => {
    expect(normalizeAvatarUrl("https://cdn.example.com/pic.png", "http://localhost:8000")).toBe(
      "https://cdn.example.com/pic.png",
    );
  });
});

describe("uploadAvatarToStorage", () => {
  it("rejects unsafe user ids", async () => {
    await expect(
      uploadAvatarToStorage(
        { baseUrl: "http://localhost:8000", anonKey: "anon" },
        "token",
        "../other-user",
        new Uint8Array([0xff, 0xd8, 0xff]),
      ),
    ).rejects.toThrow(/invalid user id/i);
  });
});
