/**
 * Optional: upload collected Android mapping from downloads/ to GlitchTip.
 * Prefer deploying files with the apps; run this only when you want GlitchTip to ingest them.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  apiRoot,
  ensureRelease,
  glitchtipOrigin,
  parseEnvFile,
  uploadProguardMapping,
} from "./glitchtip-api.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const rootEnv = path.join(root, ".env");
const androidApp = path.join(root, "apps/android/app");
const downloads = path.join(root, "docker/landing/public/downloads");

function readVersionName() {
  const gradle = fs.readFileSync(path.join(androidApp, "build.gradle.kts"), "utf8");
  const m = gradle.match(/versionName\s*=\s*"([^"]+)"/);
  return m?.[1] || "0.0.0";
}

async function main() {
  const values = parseEnvFile(rootEnv);
  const baseUrl = (values.get("TONEZEN_BASE_URL") || "").replace(/\/$/, "");
  const token = (values.get("GLITCHTIP_AUTH_TOKEN") || "").trim();
  if (!baseUrl || !token) {
    console.log("upload-android-proguard: missing URL/token, skip");
    return;
  }

  const mappingPath = path.join(downloads, "tonezen-android-mapping.txt.gz");
  if (!fs.existsSync(mappingPath)) {
    console.log("upload-android-proguard: downloads/tonezen-android-mapping.txt.gz not found — run collect-release-symbols.mjs first");
    return;
  }
  const uuidFile = path.join(downloads, "tonezen-android-proguard-uuid.txt");
  const uuid = fs.existsSync(uuidFile) ? fs.readFileSync(uuidFile, "utf8").trim() : null;

  const origin = glitchtipOrigin(baseUrl);
  const apiBase = apiRoot(origin);
  const release = `com.tonezen.app@${readVersionName()}`;
  await ensureRelease(apiBase, token, "tonezen", "tonezen-android", release);

  const res = await uploadProguardMapping(
    apiBase,
    token,
    "tonezen",
    "tonezen-android",
    mappingPath,
    uuid || undefined,
  );
  if (!res.ok && res.status !== 409) {
    const hint =
      res.status === 413
        ? " — set CLIENT_MAX_BODY_SIZE=40m on the reverse proxy in front of Kong"
        : "";
    console.warn(
      `upload-android-proguard: upload failed (${res.status})${hint}`,
      typeof res.body === "string" ? res.body.slice(0, 300) : res.body,
    );
    return;
  }
  console.log(
    `upload-android-proguard: uploaded` +
      (uuid ? ` uuid=${uuid}` : "") +
      ` → ${release}`,
  );
}

main().catch((err) => {
  console.warn("upload-android-proguard:", err?.message || err);
});
