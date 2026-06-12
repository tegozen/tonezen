#!/usr/bin/env node
/**
 * Fail if repository text files contain CRLF (breaks Docker shell entrypoints on Linux).
 *
 * Usage:
 *   node ci/check-eol.mjs         # check tracked files
 *   node ci/check-eol.mjs --fix   # convert CRLF -> LF in working tree
 */

import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fix = process.argv.includes("--fix");

const CRLF_ALLOWED = new Set([".ps1", ".bat", ".cmd"]);

const SCAN_EXTENSIONS = new Set([
  ".sh",
  ".yml",
  ".yaml",
  ".sql",
  ".mjs",
  ".cjs",
  ".js",
  ".ts",
  ".tsx",
  ".json",
  ".md",
  ".kt",
  ".kts",
  ".gradle",
  ".properties",
  ".xml",
  ".css",
  ".html",
  ".mdc",
]);

const ALWAYS_SCAN = new Set([
  "docker-compose.yml",
  "Makefile",
  ".editorconfig",
  ".gitattributes",
  ".gitignore",
]);

function listTrackedFiles() {
  const out = execFileSync("git", ["ls-files", "-z"], { cwd: ROOT });
  return out
    .toString("utf8")
    .split("\0")
    .filter(Boolean)
    .map((file) => path.join(ROOT, file));
}

function shouldScan(filePath) {
  const name = path.basename(filePath);
  if (ALWAYS_SCAN.has(name)) return true;
  const ext = path.extname(name);
  if (CRLF_ALLOWED.has(ext)) return false;
  return SCAN_EXTENSIONS.has(ext);
}

function hasCr(buffer) {
  return buffer.includes(0x0d);
}

function toLf(buffer) {
  return Buffer.from(buffer.toString("utf8").replace(/\r\n/g, "\n").replace(/\r/g, "\n"), "utf8");
}

const offenders = [];
for (const file of listTrackedFiles()) {
  if (!shouldScan(file)) continue;
  if (!fs.existsSync(file)) continue;
  const buf = fs.readFileSync(file);
  if (!hasCr(buf)) continue;
  if (fix) {
    fs.writeFileSync(file, toLf(buf));
    continue;
  }
  offenders.push(path.relative(ROOT, file));
}

if (fix) {
  console.log("Converted CRLF to LF in tracked text files.");
  process.exit(0);
}

if (offenders.length > 0) {
  console.error("CRLF line endings found (use LF in the repo):");
  for (const file of offenders.sort()) console.error(`  ${file}`);
  console.error("");
  console.error("One-time fix on this machine:");
  console.error("  git config core.autocrlf false");
  console.error("  node ci/check-eol.mjs --fix");
  console.error("  git add --renormalize .");
  process.exit(1);
}

console.log("EOL check passed (no CRLF in tracked text files).");
