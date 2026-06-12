#!/usr/bin/env node
/**
 * Ensure the default admin user exists in GoTrue (idempotent).
 *
 * Env:
 *   AUTH_INTERNAL_URL  — default http://auth:9999
 *   SERVICE_ROLE_KEY     — required
 *   ADMIN_EMAIL          — default admin@tonezen.local
 *   ADMIN_DISPLAY_NAME   — default Admin
 *   ADMIN_PASSWORD       — required
 */

import path from "node:path";
import { pathToFileURL } from "node:url";
const AUTH_INTERNAL_URL = process.env.AUTH_INTERNAL_URL ?? "http://auth:9999";
const SERVICE_ROLE_KEY = process.env.SERVICE_ROLE_KEY ?? "";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@tonezen.local";
const ADMIN_DISPLAY_NAME = process.env.ADMIN_DISPLAY_NAME ?? "Admin";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? "";

export function adminHeaders(serviceRoleKey) {
  return {
    Authorization: `Bearer ${serviceRoleKey}`,
    apikey: serviceRoleKey,
    "Content-Type": "application/json",
  };
}

export function buildAdminUserPayload(email, password, displayName = ADMIN_DISPLAY_NAME) {
  return {
    email,
    password,
    email_confirm: true,
    user_metadata: { role: "admin", full_name: displayName },
  };
}

export async function waitForAuth({
  authUrl = AUTH_INTERNAL_URL,
  fetchFn = fetch,
  maxAttempts = 30,
  delayMs = 2000,
} = {}) {
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const res = await fetchFn(`${authUrl}/health`);
      if (res.ok) return;
    } catch {
      // auth not ready yet
    }
    if (attempt < maxAttempts) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  throw new Error(`Auth not ready after ${maxAttempts} attempts (${authUrl})`);
}

export async function findUserByEmail({
  authUrl,
  serviceRoleKey,
  email,
  fetchFn = fetch,
}) {
  const res = await fetchFn(`${authUrl}/admin/users?page=1&per_page=200`, {
    headers: adminHeaders(serviceRoleKey),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`List users failed (${res.status}): ${body}`);
  }
  const data = await res.json();
  return data.users?.find((user) => user.email === email) ?? null;
}

export async function createAdminUser({
  authUrl,
  serviceRoleKey,
  email,
  password,
  fetchFn = fetch,
}) {
  const res = await fetchFn(`${authUrl}/admin/users`, {
    method: "POST",
    headers: adminHeaders(serviceRoleKey),
    body: JSON.stringify(buildAdminUserPayload(email, password)),
  });
  if (res.ok) return res.json();
  const body = await res.text();
  throw new Error(`Create admin failed (${res.status}): ${body}`);
}

export async function ensureAdminUser({
  authUrl = AUTH_INTERNAL_URL,
  serviceRoleKey = SERVICE_ROLE_KEY,
  email = ADMIN_EMAIL,
  password = ADMIN_PASSWORD,
  fetchFn = fetch,
  waitFn = waitForAuth,
} = {}) {
  if (!serviceRoleKey) {
    throw new Error("SERVICE_ROLE_KEY is required");
  }
  if (!password) {
    throw new Error("ADMIN_PASSWORD is required");
  }

  await waitFn({ authUrl, fetchFn });

  const existing = await findUserByEmail({ authUrl, serviceRoleKey, email, fetchFn });
  if (existing) {
    return { created: false, email, userId: existing.id };
  }

  const created = await createAdminUser({
    authUrl,
    serviceRoleKey,
    email,
    password,
    fetchFn,
  });
  return { created: true, email, userId: created.id ?? created.user?.id };
}

async function main() {
  const result = await ensureAdminUser();
  if (result.created) {
    console.log(`Created admin user: ${result.email}`);
  } else {
    console.log(`Admin user already exists: ${result.email}`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((err) => {
    console.error(err.message ?? err);
    process.exit(1);
  });
}
