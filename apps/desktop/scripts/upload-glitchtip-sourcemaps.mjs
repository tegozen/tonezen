/**
 * Optional: upload desktop source maps from downloads/ archive to GlitchTip.
 * Prefer deploying the archive with the apps; run this only for GlitchTip ingest.
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  apiRoot,
  ensureRelease,
  glitchtipOrigin,
  parseEnvFile,
  uploadReleaseFile,
  walkFiles,
} from "../../scripts/glitchtip-api.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootEnv = path.resolve(__dirname, "../../../.env");
const downloads = path.resolve(__dirname, "../../../docker/landing/public/downloads");
const packageJson = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, "../package.json"), "utf8"),
);

async function main() {
  const values = parseEnvFile(rootEnv);
  const baseUrl = (values.get("TONEZEN_BASE_URL") || "").replace(/\/$/, "");
  const token = (values.get("GLITCHTIP_AUTH_TOKEN") || "").trim();
  if (!baseUrl || !token) {
    console.log("upload-glitchtip-sourcemaps: missing URL/token, skip");
    return;
  }

  const archive = path.join(downloads, "tonezen-desktop-sourcemaps.tar.gz");
  const outDir = path.resolve(__dirname, "../out");
  let sourceDir = outDir;
  let tmp = null;
  if (fs.existsSync(archive)) {
    tmp = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-maps-"));
    const extracted = spawnSync("tar", ["-xzf", archive, "-C", tmp], { encoding: "utf8" });
    if (extracted.status !== 0) {
      console.warn("upload-glitchtip-sourcemaps: extract failed", extracted.stderr);
      return;
    }
    sourceDir = tmp;
  } else if (!fs.existsSync(outDir)) {
    console.log("upload-glitchtip-sourcemaps: no archive/out, skip");
    return;
  }

  const origin = glitchtipOrigin(baseUrl);
  const apiBase = apiRoot(origin);
  const org = "tonezen";
  const project = "tonezen-desktop";
  const release = `tonezen-desktop@${packageJson.version}`;

  const ensured = await ensureRelease(apiBase, token, org, project, release);
  if (!ensured.ok && ensured.status !== 208 && ensured.status !== 409) {
    console.warn(
      `upload-glitchtip-sourcemaps: create release failed (${ensured.status})`,
      typeof ensured.body === "string" ? ensured.body.slice(0, 200) : ensured.body,
    );
    return;
  }

  const maps = walkFiles(sourceDir, [".js", ".map"]);
  let uploaded = 0;
  for (const filePath of maps) {
    const rel = path.relative(sourceDir, filePath).split(path.sep).join("/");
    const name = `~/${rel}`;
    const res = await uploadReleaseFile(apiBase, token, org, project, release, filePath, name);
    if (!res.ok && res.status !== 409) {
      console.warn(`upload-glitchtip-sourcemaps: fail ${name} (${res.status})`);
      continue;
    }
    uploaded += 1;
  }
  console.log(`upload-glitchtip-sourcemaps: ${uploaded}/${maps.length} files → ${release}`);
  if (tmp) fs.rmSync(tmp, { recursive: true, force: true });
}

main().catch((err) => {
  console.warn("upload-glitchtip-sourcemaps:", err?.message || err);
});
