/**
 * Collect crash-symbol files next to release binaries in
 * docker/landing/public/downloads/ (no network).
 *
 * - tonezen-android-mapping.txt.gz
 * - tonezen-android-proguard-uuid.txt
 * - tonezen-desktop-sourcemaps.tar.gz
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import zlib from "node:zlib";
import { walkFiles } from "./glitchtip-api.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const androidApp = path.join(root, "apps/android/app");
const desktopOut = path.join(root, "apps/desktop/out");
const destDir = path.join(root, "docker/landing/public/downloads");

function findProguardUuid() {
  const roots = [
    path.join(androidApp, "build/intermediates/assets"),
    path.join(androidApp, "build/generated"),
    path.join(androidApp, "build/intermediates/sentry"),
  ];
  function walk(dir, files = []) {
    if (!fs.existsSync(dir)) return files;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full, files);
      else if (entry.name === "sentry-debug-meta.properties") files.push(full);
    }
    return files;
  }
  for (const file of roots.flatMap((r) => walk(r))) {
    const text = fs.readFileSync(file, "utf8");
    const m =
      text.match(/io\.sentry\.ProguardUuids?=(.+)/i) ||
      text.match(/proguard[_-]?uuids?=([^\s]+)/i);
    if (m?.[1]) {
      const uuid = m[1].trim().split(",")[0].trim();
      if (uuid) return uuid;
    }
  }
  return null;
}

function collectAndroid() {
  const mappingTxt = path.join(androidApp, "build/outputs/mapping/release/mapping.txt");
  if (!fs.existsSync(mappingTxt)) {
    console.log("collect-release-symbols: Android mapping.txt missing, skip");
    return null;
  }
  const gzPath = path.join(destDir, "tonezen-android-mapping.txt.gz");
  const raw = fs.readFileSync(mappingTxt);
  fs.writeFileSync(gzPath, zlib.gzipSync(raw, { level: 9 }));
  const uuid = findProguardUuid();
  const uuidPath = path.join(destDir, "tonezen-android-proguard-uuid.txt");
  if (uuid) fs.writeFileSync(uuidPath, `${uuid}\n`, "utf8");
  else if (fs.existsSync(uuidPath)) fs.unlinkSync(uuidPath);
  return { gzPath, uuid, bytes: fs.statSync(gzPath).size };
}

function collectDesktop() {
  if (!fs.existsSync(desktopOut)) {
    console.log("collect-release-symbols: apps/desktop/out missing, skip");
    return null;
  }
  const files = walkFiles(desktopOut, [".js", ".map"]);
  if (files.length === 0) {
    console.log("collect-release-symbols: no desktop js/map files, skip");
    return null;
  }
  const archive = path.join(destDir, "tonezen-desktop-sourcemaps.tar.gz");
  if (fs.existsSync(archive)) fs.unlinkSync(archive);
  // Relative paths from out/ so the archive is portable.
  const rels = files.map((f) => path.relative(desktopOut, f).split(path.sep).join("/"));
  const result = spawnSync(
    "tar",
    ["-czf", archive, "-C", desktopOut, ...rels],
    { encoding: "utf8" },
  );
  if (result.status !== 0) {
    console.warn("collect-release-symbols: tar failed", result.stderr || result.error);
    return null;
  }
  return { archive, count: files.length, bytes: fs.statSync(archive).size };
}

function main() {
  fs.mkdirSync(destDir, { recursive: true });
  const android = collectAndroid();
  if (android) {
    console.log(
      `collect-release-symbols: ${path.basename(android.gzPath)}` +
        ` (${(android.bytes / (1024 * 1024)).toFixed(1)} MiB)` +
        (android.uuid ? ` uuid=${android.uuid}` : ""),
    );
  }
  const desktop = collectDesktop();
  if (desktop) {
    console.log(
      `collect-release-symbols: ${path.basename(desktop.archive)}` +
        ` (${desktop.count} files, ${(desktop.bytes / (1024 * 1024)).toFixed(1)} MiB)`,
    );
  }
}

main();
