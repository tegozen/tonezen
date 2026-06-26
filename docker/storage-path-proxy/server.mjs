/**
 * Rewrites Supabase Storage upload paths: transliterates Cyrillic folder/file names
 * before forwarding to storage-api (which rejects non-ASCII keys).
 *
 * Read requests (GET/HEAD/OPTIONS) are forwarded verbatim: their object key already
 * comes from the catalog (the indexer recorded the real storage key) and must match
 * both the actual stored object and the URL embedded in the signed-download token.
 * Re-sanitizing a read path would change the key (e.g. lowercase it) and break the
 * signature check (`InvalidSignature`) for any object whose stored key is not already
 * the canonical sanitized form.
 */

import http from "node:http";
import { fileURLToPath } from "node:url";
import { URL } from "node:url";
import {
  rewriteObjectPathnameWithMapping,
  rewriteUploadMetadataHeaderWithMapping,
} from "../../scripts/lib/storagePathSlug.mjs";
import { upsertContentDisplayName } from "./displayNames.mjs";

const upstream = process.env.STORAGE_UPSTREAM ?? "http://storage:5000";
const port = Number(process.env.PORT ?? "5050");
const displayNameStore = {
  postgrestUrl: process.env.POSTGREST_URL ?? "",
  serviceRoleKey: process.env.SERVICE_ROLE_KEY ?? "",
};

/** @param {string | undefined} method */
export function isReadRequest(method) {
  return method === "GET" || method === "HEAD" || method === "OPTIONS";
}

/** @param {import("node:http").IncomingMessage} req */
export function rewriteRequest(req) {
  const url = new URL(req.url ?? "/", upstream);
  const headers = { ...req.headers };
  delete headers.host;

  // Reads must hit the exact stored object key; only mutating (upload) requests
  // get path/key sanitization. See module header.
  if (isReadRequest(req.method)) {
    return { url, headers, mapping: null };
  }

  const pathRewrite = rewriteObjectPathnameWithMapping(url.pathname);
  url.pathname = pathRewrite.pathname;
  let mapping = pathRewrite.mapping;

  const uploadMetadata = headers["upload-metadata"];
  if (typeof uploadMetadata === "string") {
    const metadataRewrite = rewriteUploadMetadataHeaderWithMapping(uploadMetadata);
    if (metadataRewrite.header && metadataRewrite.header !== uploadMetadata) {
      headers["upload-metadata"] = metadataRewrite.header;
    }
    mapping ??= metadataRewrite.mapping;
  }

  return { url, headers, mapping };
}

/** @param {string | undefined} method */
function shouldStoreDisplayName(method) {
  return !isReadRequest(method);
}

/**
 * @param {import("node:http").IncomingMessage} req
 * @param {import("node:http").ServerResponse} res
 */
async function proxyAsync(req, res) {
  const { url, headers, mapping } = rewriteRequest(req);

  if (shouldStoreDisplayName(req.method)) {
    await upsertContentDisplayName(displayNameStore, mapping);
  }

  const upstreamReq = http.request(
    url,
    {
      method: req.method,
      headers,
    },
    (upstreamRes) => {
      res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers);
      upstreamRes.pipe(res);
    },
  );

  upstreamReq.on("error", (error) => {
    res.writeHead(502, { "Content-Type": "text/plain" });
    res.end(`storage-path-proxy: ${error.message}`);
  });

  req.pipe(upstreamReq);
}

/**
 * @param {import("node:http").IncomingMessage} req
 * @param {import("node:http").ServerResponse} res
 */
function proxy(req, res) {
  proxyAsync(req, res).catch((error) => {
    res.writeHead(502, { "Content-Type": "text/plain" });
    res.end(`storage-path-proxy: ${error instanceof Error ? error.message : String(error)}`);
  });
}

export function createServer() {
  const server = http.createServer(proxy);
  server.on("clientError", (_error, socket) => {
    socket.end("HTTP/1.1 400 Bad Request\r\n\r\n");
  });
  return server;
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  process.on("unhandledRejection", (error) => {
    console.error("storage-path-proxy unhandled rejection:", error);
  });

  const server = createServer();
  server.listen(port, () => {
    console.log(`storage-path-proxy listening on :${port} -> ${upstream}`);
  });
}
