#!/usr/bin/env node
/**
 * Generate secrets and write .env (from .env.example template).
 *
 * Usage:
 *   node scripts/gen-env.mjs           # create .env if missing
 *   node scripts/gen-env.mjs --force   # regenerate secrets in existing .env
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");
const EXAMPLE_PATH = path.join(ROOT, ".env.example");
const ENV_PATH = path.join(ROOT, ".env");

const SECRET_KEYS = [
  "JWT_SECRET",
  "ANON_KEY",
  "SERVICE_ROLE_KEY",
  "SECRET_KEY_BASE",
  "PG_META_CRYPTO_KEY",
  "POSTGRES_PASSWORD",
  "DB_ENC_KEY",
  "GLITCHTIP_SECRET_KEY",
  "GLITCHTIP_POSTGRES_PASSWORD",
  "GLITCHTIP_ANDROID_PUBLIC_KEY",
  "GLITCHTIP_DESKTOP_PUBLIC_KEY",
  "GLITCHTIP_AUTH_TOKEN",
];

const GENERATED_IF_EMPTY_KEYS = ["ADMIN_PASSWORD", "DASHBOARD_PASSWORD"];

const force = process.argv.includes("--force");

function randomSecret(bytes = 32) {
  return crypto.randomBytes(bytes).toString("base64url");
}

function randomPassword(length = 16) {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  let out = "";
  while (out.length < length) {
    out += alphabet[crypto.randomInt(alphabet.length)];
  }
  return out;
}

function randomHexKey(bytes = 16) {
  return crypto.randomBytes(bytes).toString("hex");
}

function base64urlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

function signJwt(payload, secret) {
  const header = base64urlJson({ alg: "HS256", typ: "JWT" });
  const body = base64urlJson(payload);
  const data = `${header}.${body}`;
  const signature = crypto.createHmac("sha256", secret).update(data).digest("base64url");
  return `${data}.${signature}`;
}

function generateSecrets() {
  const jwtSecret = randomSecret(32);
  const exp = Math.floor(Date.now() / 1000) + 10 * 365 * 24 * 3600;
  return {
    JWT_SECRET: jwtSecret,
    ANON_KEY: signJwt({ iss: "supabase", role: "anon", exp }, jwtSecret),
    SERVICE_ROLE_KEY: signJwt({ iss: "supabase", role: "service_role", exp }, jwtSecret),
    SECRET_KEY_BASE: randomSecret(64),
    PG_META_CRYPTO_KEY: randomSecret(32),
    POSTGRES_PASSWORD: randomPassword(24),
    DB_ENC_KEY: crypto.randomBytes(8).toString("hex"), // Realtime AES-128: exactly 16 chars
    GLITCHTIP_SECRET_KEY: randomSecret(32),
    GLITCHTIP_POSTGRES_PASSWORD: randomPassword(24),
    GLITCHTIP_ANDROID_PUBLIC_KEY: randomHexKey(16),
    GLITCHTIP_DESKTOP_PUBLIC_KEY: randomHexKey(16),
    GLITCHTIP_AUTH_TOKEN: randomHexKey(32),
  };
}

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

function renderEnv(template, values) {
  const lines = template.split(/\r?\n/);
  const out = [];
  const written = new Set();

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || !trimmed.includes("=")) {
      out.push(line);
      continue;
    }
    const key = trimmed.slice(0, trimmed.indexOf("="));
    if (values.has(key)) {
      out.push(`${key}=${values.get(key)}`);
      written.add(key);
    } else {
      out.push(line);
    }
  }

  for (const [key, value] of values) {
    if (!written.has(key)) {
      out.push(`${key}=${value}`);
    }
  }

  return `${out.join("\n").replace(/\n?$/, "\n")}`;
}

function main() {
  if (!fs.existsSync(EXAMPLE_PATH)) {
    console.error("Missing .env.example");
    process.exit(1);
  }

  if (fs.existsSync(ENV_PATH) && !force) {
    console.error(".env already exists. Use --force to regenerate secrets.");
    process.exit(1);
  }

  const template = fs.existsSync(ENV_PATH) && force
    ? fs.readFileSync(ENV_PATH, "utf8")
    : fs.readFileSync(EXAMPLE_PATH, "utf8");

  const values = parseEnv(template);
  const secrets = generateSecrets();
  for (const key of SECRET_KEYS) {
    values.set(key, secrets[key]);
  }
  for (const key of GENERATED_IF_EMPTY_KEYS) {
    if (!values.get(key)) {
      values.set(key, randomPassword());
    }
  }

  fs.writeFileSync(ENV_PATH, renderEnv(template, values), "utf8");

  console.log(`Wrote ${ENV_PATH}`);
  console.log("Generated secrets for: " + SECRET_KEYS.join(", "));
  console.log("Passwords were written to .env only (not printed).");
  console.log("");
  console.log("Fill S3_* in .env from Beget panel (Object storage → Access keys).");
  console.log("");
  console.log("Next:");
  console.log("  docker compose up -d --build");
  console.log("  cd apps/desktop && npm run dev   # picks up ANON_KEY + GlitchTip DSN from .env");
  console.log("  Android release: assembleRelease reads GlitchTip DSN from root .env");
}

main();
