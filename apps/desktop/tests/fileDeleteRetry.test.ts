import { describe, expect, it, vi } from "vitest";
import { retryFileDelete, rmWithRetry, unlinkWithRetry } from "../src/shared/fileDeleteRetry.js";
import fsPromises from "node:fs/promises";
import path from "node:path";
import fs from "node:fs";
import os from "node:os";

describe("retryFileDelete", () => {
  it("retries EBUSY then succeeds", async () => {
    let calls = 0;
    await retryFileDelete(
      async () => {
        calls += 1;
        if (calls < 3) {
          const error = new Error("busy") as NodeJS.ErrnoException;
          error.code = "EBUSY";
          throw error;
        }
      },
      { attempts: 5, delayMs: 1 },
    );
    expect(calls).toBe(3);
  });

  it("ignores ENOENT", async () => {
    await expect(
      retryFileDelete(async () => {
        const error = new Error("missing") as NodeJS.ErrnoException;
        error.code = "ENOENT";
        throw error;
      }),
    ).resolves.toBeUndefined();
  });

  it("throws non-retryable errors immediately", async () => {
    await expect(
      retryFileDelete(async () => {
        const error = new Error("bad") as NodeJS.ErrnoException;
        error.code = "EACCES";
        throw error;
      }),
    ).rejects.toMatchObject({ code: "EACCES" });
  });
});

describe("unlinkWithRetry and rmWithRetry", () => {
  it("deletes files and directories", async () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-delete-test-"));
    const filePath = path.join(root, "track.mp3");
    const nestedDir = path.join(root, "book", "nested");
    fs.mkdirSync(nestedDir, { recursive: true });
    fs.writeFileSync(filePath, "audio");
    fs.writeFileSync(path.join(nestedDir, "other.mp3"), "audio");

    await unlinkWithRetry(filePath);
    expect(fs.existsSync(filePath)).toBe(false);

    await rmWithRetry(root);
    expect(fs.existsSync(root)).toBe(false);
  });

  it("retries rm when first attempt fails with EBUSY", async () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-delete-busy-"));
    fs.writeFileSync(path.join(root, "a.mp3"), "x");
    let attempts = 0;
    const originalRm = fsPromises.rm.bind(fsPromises);
    vi.spyOn(fsPromises, "rm").mockImplementation(async (target, opts) => {
      attempts += 1;
      if (attempts === 1) {
        throw Object.assign(new Error("busy"), { code: "EBUSY" });
      }
      return originalRm(target, opts);
    });

    await rmWithRetry(root);
    expect(attempts).toBe(2);
    expect(fs.existsSync(root)).toBe(false);
    vi.restoreAllMocks();
  });
});
