/**
 * GlitchTip HTTP helpers — work with path-prefixed installs
 * (e.g. https://host/glitchtip) where sentry-cli rejects the URL.
 */
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

export function parseEnvFile(envPath) {
  const map = new Map();
  if (!fs.existsSync(envPath)) return map;
  for (const line of fs.readFileSync(envPath, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    map.set(trimmed.slice(0, eq), trimmed.slice(eq + 1));
  }
  return map;
}

/** Public GlitchTip origin including /glitchtip prefix, no trailing slash. */
export function glitchtipOrigin(baseUrl) {
  const root = String(baseUrl || "").replace(/\/$/, "");
  if (!root) return "";
  return `${root}/glitchtip`;
}

export function apiRoot(origin) {
  return `${origin.replace(/\/$/, "")}/api/0`;
}

export async function glitchtipFetch(apiBase, token, pathname, options = {}) {
  const url = `${apiBase}${pathname.startsWith("/") ? pathname : `/${pathname}`}`;
  const { timeoutMs = 20_000, headers: inputHeaders, ...fetchOptions } = options;
  const headers = new Headers(inputHeaders || {});
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...fetchOptions, headers, signal: controller.signal });
    const text = await res.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      // keep text
    }
    return { ok: res.ok, status: res.status, body, text };
  } catch (err) {
    const message = err?.name === "AbortError" ? `timeout after ${timeoutMs}ms` : err?.message || String(err);
    return { ok: false, status: 0, body: message, text: message };
  } finally {
    clearTimeout(timer);
  }
}

/** Create release if missing (idempotent). */
export async function ensureRelease(apiBase, token, org, project, version) {
  const create = await glitchtipFetch(apiBase, token, `/organizations/${org}/releases/`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ version, projects: [project] }),
  });
  if (create.ok || create.status === 208 || create.status === 409) return create;
  // Already exists → GET
  if (create.status === 400 || create.status === 380) {
    const get = await glitchtipFetch(
      apiBase,
      token,
      `/organizations/${org}/releases/${encodeURIComponent(version)}/`,
    );
    if (get.ok) return get;
  }
  return create;
}

/** Upload a release file (source maps / artifacts). */
export async function uploadReleaseFile(apiBase, token, org, project, version, filePath, name) {
  const bytes = fs.readFileSync(filePath);
  const form = new FormData();
  form.append("name", name);
  form.append("file", new Blob([bytes]), path.basename(filePath));
  return glitchtipFetch(
    apiBase,
    token,
    `/projects/${org}/${project}/releases/${encodeURIComponent(version)}/files/`,
    { method: "POST", body: form, timeoutMs: 60_000 },
  );
}

/**
 * Upload ProGuard/R8 mapping as a debug file with UUID
 * (Sentry-compatible /files/dsyms/ endpoint used by GlitchTip).
 * Mapping.txt is gzipped before upload (~10× smaller; nginx body limits).
 */
export async function uploadProguardMapping(apiBase, token, org, project, mappingPath, uuid) {
  const raw = fs.readFileSync(mappingPath);
  const isGz = mappingPath.endsWith(".gz");
  const bytes = isGz ? raw : zlib.gzipSync(raw, { level: 9 });
  const filename = isGz ? path.basename(mappingPath) : `${path.basename(mappingPath)}.gz`;
  const form = new FormData();
  form.append("file", new Blob([bytes]), filename);
  const qs = uuid ? `?uuid=${encodeURIComponent(uuid)}` : "";
  return glitchtipFetch(apiBase, token, `/projects/${org}/${project}/files/dsyms/${qs}`, {
    method: "POST",
    body: form,
    timeoutMs: 120_000,
  });
}

export function walkFiles(dir, exts, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walkFiles(full, exts, out);
    else if (exts.some((e) => entry.name.endsWith(e))) out.push(full);
  }
  return out;
}
