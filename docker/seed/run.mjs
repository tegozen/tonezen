/**
 * Idempotent post-start seeds: GoTrue admin + Storage layout folders.
 * Run via `docker compose up` (seed service) or `make seed`.
 */

import { ensureAdminUser, waitForAuthReady } from "./admin-user.mjs";
import { ensureContentLayout, waitForStorageReady } from "./storage-layout.mjs";

async function main() {
  const serviceKey = process.env.SERVICE_ROLE_KEY;
  if (!serviceKey) {
    throw new Error("SERVICE_ROLE_KEY is required");
  }

  const authUrl = process.env.AUTH_URL ?? "http://auth:9999";
  const storageUrl = process.env.STORAGE_URL ?? "http://storage:5000";
  const storageBucket = process.env.STORAGE_BUCKET ?? "content";
  const adminEmail = process.env.ADMIN_EMAIL ?? "admin@tonezen.local";
  const adminPassword = process.env.ADMIN_PASSWORD;
  const adminDisplayName = process.env.ADMIN_DISPLAY_NAME ?? "Admin";

  await waitForAuthReady({ authUrl });
  const admin = await ensureAdminUser({
    authUrl,
    serviceKey,
    email: adminEmail,
    password: adminPassword,
    displayName: adminDisplayName,
  });
  console.log(
    admin.created
      ? `Created admin user: ${admin.email}`
      : `Admin user already exists: ${admin.email}`,
  );

  await waitForStorageReady({ storageUrl });
  const layout = await ensureContentLayout({
    storageUrl,
    serviceKey,
    bucket: storageBucket,
  });
  console.log(
    `Storage layout in bucket '${layout.bucket}': ${layout.created.join(", ")}`,
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
