import { randomUUID } from "node:crypto";
import { createWriteStream } from "node:fs";
import { unlink } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import os from "node:os";
import path from "node:path";

export interface StorageDownloadConfig {
  storageUrl: string;
  bucket: string;
  serviceRoleKey: string;
}

const DOWNLOAD_TIMEOUT_MS = 120_000;
const MAX_OBJECT_BYTES = 500 * 1024 * 1024;

function encodeStoragePath(storagePath: string): string {
  return storagePath
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
}

function safeTempBasename(storagePath: string): string {
  const raw = path.posix.basename(storagePath.replaceAll("\\", "/")) || "object";
  return raw.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 80) || "object";
}

export async function downloadObjectToTemp(
  storagePath: string,
  config: StorageDownloadConfig,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  const url = `${config.storageUrl}/object/${config.bucket}/${encodeStoragePath(storagePath)}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), DOWNLOAD_TIMEOUT_MS);
  try {
    const res = await fetchImpl(url, {
      headers: {
        Authorization: `Bearer ${config.serviceRoleKey}`,
        apikey: config.serviceRoleKey,
      },
      signal: controller.signal,
    });
    if (!res.ok) {
      throw new Error(`Storage download failed (${res.status}) for ${storagePath}`);
    }
    const contentLength = Number(res.headers.get("content-length") ?? "0");
    if (Number.isFinite(contentLength) && contentLength > MAX_OBJECT_BYTES) {
      throw new Error(`Storage object too large for ${storagePath}`);
    }
    if (!res.body) {
      throw new Error(`Storage download missing body for ${storagePath}`);
    }

    const tempPath = path.join(
      os.tmpdir(),
      `tonezen-indexer-${randomUUID()}-${safeTempBasename(storagePath)}`,
    );
    const nodeStream = Readable.fromWeb(res.body as import("node:stream/web").ReadableStream);
    let written = 0;
    nodeStream.on("data", (chunk: Buffer | string) => {
      written += typeof chunk === "string" ? Buffer.byteLength(chunk) : chunk.length;
      if (written > MAX_OBJECT_BYTES) {
        controller.abort();
        throw new Error(`Storage object too large for ${storagePath}`);
      }
    });
    await pipeline(nodeStream, createWriteStream(tempPath));
    return tempPath;
  } finally {
    clearTimeout(timer);
  }
}

export async function removeTempFile(filePath: string): Promise<void> {
  await unlink(filePath).catch(() => undefined);
}
