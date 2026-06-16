import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootEnv = path.resolve(__dirname, "../../../.env");
const outPath = path.resolve(__dirname, "../client.env");

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

const values = parseEnv(fs.readFileSync(rootEnv, "utf8"));
const baseUrl = values.get("TONEZEN_BASE_URL");
const anonKey = values.get("ANON_KEY");

if (!baseUrl || !anonKey) {
  console.error("Missing TONEZEN_BASE_URL or ANON_KEY in root .env");
  process.exit(1);
}

fs.writeFileSync(outPath, `TONEZEN_BASE_URL=${baseUrl}\nANON_KEY=${anonKey}\n`, "utf8");
console.log(`Wrote ${outPath}`);
