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

function buildGlitchtipDsn(baseUrl, publicKey, projectId) {
  const key = String(publicKey || "")
    .trim()
    .replace(/-/g, "");
  const id = String(projectId || "").trim();
  if (!baseUrl || !key || !id) return "";
  let url;
  try {
    url = new URL(String(baseUrl).trim());
  } catch {
    return "";
  }
  const host = url.port ? `${url.hostname}:${url.port}` : url.hostname;
  return `${url.protocol}//${key}@${host}/glitchtip/${id}`;
}

const values = parseEnv(fs.readFileSync(rootEnv, "utf8"));
const baseUrl = values.get("TONEZEN_BASE_URL");
const anonKey = values.get("ANON_KEY");

if (!baseUrl || !anonKey) {
  console.error("Missing TONEZEN_BASE_URL or ANON_KEY in root .env");
  process.exit(1);
}

const desktopDsn =
  (values.get("GLITCHTIP_DESKTOP_DSN") || "").trim() ||
  buildGlitchtipDsn(baseUrl, values.get("GLITCHTIP_DESKTOP_PUBLIC_KEY") || "", 2);

const lines = [`TONEZEN_BASE_URL=${baseUrl}`, `ANON_KEY=${anonKey}`];
if (desktopDsn) {
  lines.push(`GLITCHTIP_DESKTOP_DSN=${desktopDsn}`);
}

fs.writeFileSync(outPath, `${lines.join("\n")}\n`, "utf8");
console.log(`Wrote ${outPath}`);
