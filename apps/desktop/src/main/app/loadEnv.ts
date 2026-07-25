import dotenv from "dotenv";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const DEFAULT_BASE_URL = "http://localhost:8000";
const DEFAULT_ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0";

/** Load .env from monorepo root and apps/desktop (first found wins per variable). */
export function loadAppEnv(): void {
  const candidates = [
    path.resolve(process.cwd(), ".env"),
    path.resolve(process.cwd(), "../../.env"),
    path.resolve(__dirname, "../../../.env"),
    path.resolve(__dirname, "../../.env"),
  ];

  const seen = new Set<string>();
  for (const envPath of candidates) {
    if (!seen.has(envPath) && fs.existsSync(envPath)) {
      dotenv.config({ path: envPath, override: false });
      seen.add(envPath);
    }
  }
}

/** Candidate .env paths for packaged Electron apps (platform-specific layout). */
export function packagedEnvCandidates(execPath: string): string[] {
  const binaryDir = path.dirname(execPath);
  return [
    path.join(binaryDir, ".env"),
    // macOS: electron-builder extraFiles land in Contents/, binary in Contents/MacOS/
    path.join(binaryDir, "..", ".env"),
  ];
}

export function loadPackagedEnv(execPath: string): void {
  for (const envPath of packagedEnvCandidates(execPath)) {
    const resolved = path.resolve(envPath);
    if (fs.existsSync(resolved)) {
      dotenv.config({ path: resolved, override: true });
      return;
    }
  }
}

export function normalizeBaseUrl(url: string): string {
  return url.replace(/\/$/, "");
}

export function getClientConfig() {
  return {
    baseUrl: normalizeBaseUrl(process.env.TONEZEN_BASE_URL ?? DEFAULT_BASE_URL),
    supabaseAnonKey: process.env.ANON_KEY ?? process.env.TONEZEN_SUPABASE_ANON_KEY ?? DEFAULT_ANON_KEY,
  };
}
