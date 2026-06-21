/**
 * Rewrites Supabase Storage upload paths: transliterates Cyrillic folder/file names
 * before forwarding to storage-api (which rejects non-ASCII keys).
 */

import http from "node:http";
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

/** @param {import("node:http").IncomingMessage} req */
function rewriteRequest(req) {
  const url = new URL(req.url ?? "/", upstream);
  const pathRewrite = rewriteObjectPathnameWithMapping(url.pathname);
  url.pathname = pathRewrite.pathname;
  let mapping = pathRewrite.mapping;

  const headers = { ...req.headers };
  const uploadMetadata = headers["upload-metadata"];
  if (typeof uploadMetadata === "string") {
    const metadataRewrite = rewriteUploadMetadataHeaderWithMapping(uploadMetadata);
    if (metadataRewrite.header && metadataRewrite.header !== uploadMetadata) {
      headers["upload-metadata"] = metadataRewrite.header;
    }
    mapping ??= metadataRewrite.mapping;
  }

  delete headers.host;
  return { url, headers, mapping };
}

/** @param {string | undefined} method */
function shouldStoreDisplayName(method) {
  return method !== "GET" && method !== "HEAD" && method !== "OPTIONS";
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

const server = http.createServer(proxy);

server.on("clientError", (_error, socket) => {
  socket.end("HTTP/1.1 400 Bad Request\r\n\r\n");
});

process.on("unhandledRejection", (error) => {
  console.error("storage-path-proxy unhandled rejection:", error);
});

server.listen(port, () => {
  console.log(`storage-path-proxy listening on :${port} -> ${upstream}`);
});
