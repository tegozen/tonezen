import { describe, expect, it, vi } from "vitest";
import { signStoragePath, signStoragePaths, toPublicDownloadUrl } from "../src/lib/storageSign.js";

describe("toPublicDownloadUrl", () => {
  it("prefixes relative storage sign paths with public base and /storage/v1", () => {
    expect(
      toPublicDownloadUrl(
        "/object/sign/content/music/a.mp3?token=x",
        "http://localhost:8000",
      ),
    ).toBe("http://localhost:8000/storage/v1/object/sign/content/music/a.mp3?token=x");
  });
});

describe("signStoragePath", () => {
  it("requests signed URL from storage API", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        signedURL: "/object/sign/content/music/a/audio/1.mp3?token=abc",
      }),
    });

    const url = await signStoragePath(
      "music/a/audio/1.mp3",
      {
        storageUrl: "http://storage:5000",
        publicBaseUrl: "http://localhost:8000",
        bucket: "content",
        serviceRoleKey: "service-role-key",
        expiresIn: 900,
      },
      fetchMock,
    );

    expect(url).toBe(
      "http://localhost:8000/storage/v1/object/sign/content/music/a/audio/1.mp3?token=abc",
    );
    expect(fetchMock).toHaveBeenCalledWith(
      "http://storage:5000/object/sign/content/music/a/audio/1.mp3",
      expect.objectContaining({ method: "POST" }),
    );
  });
});

describe("signStoragePaths", () => {
  it("deduplicates paths before signing", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue({
        ok: true,
        json: async () => ({ signedURL: "http://localhost/signed" }),
      });

    const signed = await signStoragePaths(
      ["a/1.mp3", "a/1.mp3", "b/2.mp3"],
      {
        storageUrl: "http://storage:5000",
        publicBaseUrl: "http://localhost:8000",
        bucket: "content",
        serviceRoleKey: "key",
        expiresIn: 60,
      },
      fetchMock,
    );

    expect(signed.size).toBe(2);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
