/**
 * Ensure cycles/ and music/ prefixes exist in Storage (Studio shows folders via placeholder objects).
 */

export const LAYOUT_DIRS = ["cycles", "music"];
export const DEFAULT_BUCKET = "content";
const PLACEHOLDER = ".gitkeep";

function authHeaders(serviceKey) {
  return {
    Authorization: `Bearer ${serviceKey}`,
    apikey: serviceKey,
    "Content-Type": "text/plain",
    "x-upsert": "true",
  };
}

export async function waitForStorageReady({
  storageUrl,
  fetchFn = fetch,
  maxAttempts = 60,
  delayMs = 2000,
}) {
  const base = storageUrl.replace(/\/$/, "");

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const res = await fetchFn(`${base}/status`);
      if (res.ok) {
        return;
      }
    } catch {
      // Storage still starting.
    }

    if (attempt < maxAttempts) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }

  throw new Error(`Storage not ready after ${maxAttempts} attempts: ${storageUrl}`);
}

export async function uploadLayoutObject({
  storageUrl,
  serviceKey,
  bucket = DEFAULT_BUCKET,
  objectPath,
  fetchFn = fetch,
}) {
  const base = storageUrl.replace(/\/$/, "");
  const res = await fetchFn(`${base}/object/${bucket}/${objectPath}`, {
    method: "POST",
    headers: authHeaders(serviceKey),
    body: "",
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Upload ${objectPath} failed (${res.status}): ${text}`);
  }
  return objectPath;
}

export async function ensureContentLayout({
  storageUrl,
  serviceKey,
  bucket = DEFAULT_BUCKET,
  fetchFn = fetch,
}) {
  const created = [];

  for (const dir of LAYOUT_DIRS) {
    const objectPath = `${dir}/${PLACEHOLDER}`;
    await uploadLayoutObject({
      storageUrl,
      serviceKey,
      bucket,
      objectPath,
      fetchFn,
    });
    created.push(objectPath);
  }

  return { created, ensuredDirs: LAYOUT_DIRS, bucket };
}

async function main() {
  const storageUrl = process.env.STORAGE_URL ?? "http://storage:5000";
  const serviceKey = process.env.SERVICE_ROLE_KEY;
  const bucket = process.env.STORAGE_BUCKET ?? DEFAULT_BUCKET;

  if (!serviceKey) {
    throw new Error("SERVICE_ROLE_KEY is required");
  }

  await waitForStorageReady({ storageUrl });
  const result = await ensureContentLayout({ storageUrl, serviceKey, bucket });
  console.log(
    `Storage layout in bucket '${bucket}': ${result.created.join(", ")}`,
  );
}

if (import.meta.url === new URL(process.argv[1], "file:").href) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
