/**
 * Best-effort upload of desktop source maps to self-hosted GlitchTip.
 * Skips cleanly when token/base URL are missing or upload fails (build must not break).
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootEnv = path.resolve(__dirname, "../../../.env");
const packageJson = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, "../package.json"), "utf8"),
);

function parseEnv(content) {
  const map = new Map();
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    map.set(trimmed.slice(0, eq), trimmed.slice(eq + 1));
  }
  return map;
}

function main() {
  if (!fs.existsSync(rootEnv)) {
    console.log("upload-glitchtip-sourcemaps: no root .env, skip");
    return;
  }
  const values = parseEnv(fs.readFileSync(rootEnv, "utf8"));
  const baseUrl = (values.get("TONEZEN_BASE_URL") || "").replace(/\/$/, "");
  const token = (values.get("GLITCHTIP_AUTH_TOKEN") || "").trim();
  if (!baseUrl || !token) {
    console.log("upload-glitchtip-sourcemaps: missing URL/token, skip");
    return;
  }

  const release = `tonezen-desktop@${packageJson.version}`;
  const url = `${baseUrl}/glitchtip`;
  const outDir = path.resolve(__dirname, "../out");
  if (!fs.existsSync(outDir)) {
    console.log("upload-glitchtip-sourcemaps: no out/, skip");
    return;
  }

  let sentryCli;
  try {
    const require = createRequire(import.meta.url);
    sentryCli = require.resolve("@sentry/cli/bin/sentry-cli");
  } catch {
    console.log("upload-glitchtip-sourcemaps: @sentry/cli not installed, skip");
    return;
  }

  const env = {
    ...process.env,
    SENTRY_AUTH_TOKEN: token,
    SENTRY_URL: url,
    SENTRY_ORG: "tonezen",
    SENTRY_PROJECT: "tonezen-desktop",
  };

  const steps = [
    ["releases", "new", release],
    [
      "releases",
      "files",
      release,
      "upload-sourcemaps",
      outDir,
      "--rewrite",
      "--ext",
      "js",
      "--ext",
      "map",
    ],
  ];

  for (const args of steps) {
    const result = spawnSync(process.execPath, [sentryCli, "--url", url, ...args], {
      env,
      encoding: "utf8",
    });
    if (result.status !== 0) {
      console.warn(
        `upload-glitchtip-sourcemaps: step failed (${args.join(" ")}), continuing build`,
      );
      if (result.stderr) console.warn(result.stderr.trim());
      return;
    }
  }
  console.log(`upload-glitchtip-sourcemaps: uploaded ${release}`);
}

main();
