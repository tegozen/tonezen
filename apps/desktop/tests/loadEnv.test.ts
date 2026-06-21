import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { apiV1Url } from "../src/shared/serverPaths.js";
import {
  getClientConfig,
  loadAppEnv,
  loadPackagedEnv,
  packagedEnvCandidates,
} from "../src/main/loadEnv.js";

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

describe("loadPackagedEnv", () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it("loads .env from macOS Contents/ when binary is in Contents/MacOS/", () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-mac-app-"));
    const contentsDir = path.join(root, "Tonezen.app", "Contents");
    const macOsDir = path.join(contentsDir, "MacOS");
    fs.mkdirSync(macOsDir, { recursive: true });
    fs.writeFileSync(
      path.join(contentsDir, ".env"),
      "TONEZEN_BASE_URL=https://tonezen.example\nANON_KEY=test-anon-key\n",
    );

    const execPath = path.join(macOsDir, "Tonezen");
    expect(packagedEnvCandidates(execPath)).toContain(path.join(contentsDir, ".env"));

    delete process.env.TONEZEN_BASE_URL;
    delete process.env.ANON_KEY;
    loadPackagedEnv(execPath);

    const config = getClientConfig();
    expect(config.baseUrl).toBe("https://tonezen.example");
    expect(config.supabaseAnonKey).toBe("test-anon-key");

    fs.rmSync(root, { recursive: true, force: true });
  });

  it("loads .env beside the Windows executable", () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-win-app-"));
    fs.writeFileSync(
      path.join(root, ".env"),
      "TONEZEN_BASE_URL=https://tonezen.example\nANON_KEY=test-anon-key\n",
    );

    const execPath = path.join(root, "Tonezen.exe");
    delete process.env.TONEZEN_BASE_URL;
    delete process.env.ANON_KEY;
    loadPackagedEnv(execPath);

    const config = getClientConfig();
    expect(config.baseUrl).toBe("https://tonezen.example");
    expect(config.supabaseAnonKey).toBe("test-anon-key");

    fs.rmSync(root, { recursive: true, force: true });
  });
});
