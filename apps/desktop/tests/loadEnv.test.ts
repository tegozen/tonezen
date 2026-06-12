import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { apiV1Url } from "../src/shared/serverPaths.js";
import { getClientConfig, loadAppEnv } from "../src/main/loadEnv.js";

describe("loadAppEnv", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it("loads TONEZEN_BASE_URL from a temp .env file", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-env-"));
    const envFile = path.join(dir, ".env");
    fs.writeFileSync(envFile, "TONEZEN_BASE_URL=https://example.com\n");

    const prevCwd = process.cwd();
    try {
      process.chdir(dir);
      delete process.env.TONEZEN_BASE_URL;
      loadAppEnv();
      const config = getClientConfig();
      expect(config.baseUrl).toBe("https://example.com");
      expect(apiV1Url(config.baseUrl, "/catalog/cycles")).toBe(
        "https://example.com/api/v1/catalog/cycles",
      );
    } finally {
      process.chdir(prevCwd);
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});
