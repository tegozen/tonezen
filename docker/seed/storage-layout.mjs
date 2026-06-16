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

export async function ensureContentLayout({
  storageUrl,
  serviceKey,
  bucket = DEFAULT_BUCKET,
  fetchFn = fetch,
}) {
  const base = storageUrl.replace(/\/$/, "");
  const created = [];

  for (const dir of LAYOUT_DIRS) {
    const objectPath = `${dir}/${PLACEHOLDER}`;
    const res = await fetchFn(`${base}/object/${bucket}/${objectPath}`, {
      method: "POST",
      headers: authHeaders(serviceKey),
      body: "",
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`Upload ${objectPath} failed (${res.status}): ${text}`);
    }
    created.push(objectPath);
  }

  return { created, ensuredDirs: LAYOUT_DIRS, bucket };
}
