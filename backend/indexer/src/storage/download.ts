import { randomUUID } from "node:crypto";
import { unlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

export interface StorageDownloadConfig {
  storageUrl: string;
  bucket: string;
  serviceRoleKey: string;
}

function encodeStoragePath(storagePath: string): string {
  return storagePath
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
}

export async function downloadObjectToTemp(
  storagePath: string,
  config: StorageDownloadConfig,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  const url = `${config.storageUrl}/object/${config.bucket}/${encodeStoragePath(storagePath)}`;
  const res = await fetchImpl(url, {
    headers: {
      Authorization: `Bearer ${config.serviceRoleKey}`,
      apikey: config.serviceRoleKey,
    },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Storage download failed (${res.status}) for ${storagePath}: ${body}`);
  }

  const basename = path.basename(storagePath) || "object";
  const tempPath = path.join(os.tmpdir(), `tonezen-indexer-${randomUUID()}-${basename}`);
  await writeFile(tempPath, Buffer.from(await res.arrayBuffer()));
  return tempPath;
}

export async function removeTempFile(filePath: string): Promise<void> {
  await unlink(filePath).catch(() => undefined);
}
