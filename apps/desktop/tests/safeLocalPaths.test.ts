import path from "node:path";
import { describe, expect, it } from "vitest";
import {
  assertAllowedDownloadUrl,
  isPathUnderRoot,
  isSafeStorageId,
  normalizeDownloadUrl,
  resolveTrackDownloadPath,
  sanitizeLocalAudioPath,
} from "../src/shared/safeLocalPaths.js";

describe("isSafeStorageId", () => {
  it("accepts plain ids", () => {
    expect(isSafeStorageId("book-123")).toBe(true);
    expect(isSafeStorageId("3957cba3-e20f-47f6-bd74-f4c5beaf7d08")).toBe(true);
  });

  it("rejects traversal and separators", () => {
    expect(isSafeStorageId("../etc")).toBe(false);
    expect(isSafeStorageId("book/id")).toBe(false);
    expect(isSafeStorageId("book\\id")).toBe(false);
    expect(isSafeStorageId("")).toBe(false);
  });
});

describe("resolveTrackDownloadPath", () => {
  const root = path.resolve("/data/downloads");

  it("builds paths under downloads root", () => {
    expect(resolveTrackDownloadPath(root, "book1", "track1")).toBe(
      path.resolve(root, "book1", "track1.mp3"),
    );
  });

  it("rejects unsafe ids", () => {
    expect(resolveTrackDownloadPath(root, "../escape", "track1")).toBeNull();
    expect(resolveTrackDownloadPath(root, "book1", "../../secret")).toBeNull();
  });
});

describe("sanitizeLocalAudioPath", () => {
  const root = path.resolve("/data/downloads");

  it("allows files under allowed roots", () => {
    const file = path.resolve(root, "book1", "track1.mp3");
    expect(sanitizeLocalAudioPath(file, [root])).toBe(file);
  });

  it("rejects paths outside allowed roots", () => {
    expect(sanitizeLocalAudioPath("C:/Windows/System32/config.sys", [root])).toBeNull();
    expect(sanitizeLocalAudioPath(path.resolve(root, "..", "session.dat"), [root])).toBeNull();
  });
});

describe("isPathUnderRoot", () => {
  it("detects traversal attempts", () => {
    const root = path.resolve("/data/downloads");
    expect(isPathUnderRoot(root, path.resolve(root, "book", "track.mp3"))).toBe(true);
    expect(isPathUnderRoot(root, path.resolve(root, "..", "session.dat"))).toBe(false);
  });
});

describe("assertAllowedDownloadUrl", () => {
  it("accepts same-origin signed URLs", () => {
    expect(() =>
      assertAllowedDownloadUrl(
        "http://localhost:8000/storage/v1/object/sign/content/a.mp3?token=x",
        "http://localhost:8000",
      ),
    ).not.toThrow();
  });

  it("rejects foreign origins without storage sign path", () => {
    expect(() =>
      assertAllowedDownloadUrl("http://evil.example/file.mp3", "http://localhost:8000"),
    ).toThrow(/origin mismatch/i);
  });

  it("rejects non-http schemes", () => {
    expect(() =>
      assertAllowedDownloadUrl("file:///etc/passwd", "http://localhost:8000"),
    ).toThrow(/Invalid download URL/i);
  });
});

describe("normalizeDownloadUrl", () => {
  it("rewrites foreign storage sign URLs to API origin", () => {
    expect(
      normalizeDownloadUrl(
        "https://internal.supabase.example/storage/v1/object/sign/content/a.mp3?token=x",
        "https://tonezen.tegozen.ru",
      ),
    ).toBe("https://tonezen.tegozen.ru/storage/v1/object/sign/content/a.mp3?token=x");
  });

  it("allows assert after normalization", () => {
    expect(() =>
      assertAllowedDownloadUrl(
        "https://internal.supabase.example/storage/v1/object/sign/content/a.mp3?token=x",
        "https://tonezen.tegozen.ru",
      ),
    ).not.toThrow();
  });
});
