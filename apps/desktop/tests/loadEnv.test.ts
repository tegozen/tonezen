import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { getClientConfig, loadAppEnv } from "../src/main/loadEnv.js";

describe("loadAppEnv", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it("loads TPLAYER_* from a temp .env file", () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "tplayer-env-"));
    const envFile = path.join(dir, ".env");
    fs.writeFileSync(
      envFile,
      "TPLAYER_API_URL=https://example.com/api/v1\nTPLAYER_SUPABASE_URL=https://example.com\n",
    );

    const prevCwd = process.cwd();
    try {
      process.chdir(dir);
      delete process.env.TPLAYER_API_URL;
      delete process.env.TPLAYER_SUPABASE_URL;
      loadAppEnv();
      const config = getClientConfig();
      expect(config.apiBaseUrl).toBe("https://example.com/api/v1");
      expect(config.supabaseUrl).toBe("https://example.com");
    } finally {
      process.chdir(prevCwd);
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});
