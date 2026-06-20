/**
 * Rewrites Supabase Storage upload paths: transliterates Cyrillic folder/file names
 * before forwarding to storage-api (which rejects non-ASCII keys).
 */

import http from "node:http";
import { URL } from "node:url";
import {
  rewriteObjectPathname,
  rewriteUploadMetadataHeader,
} from "../../scripts/lib/storagePathSlug.mjs";

const upstream = process.env.STORAGE_UPSTREAM ?? "http://storage:5000";
const port = Number(process.env.PORT ?? "5050");

/** @param {import("node:http").IncomingMessage} req */
function rewriteRequest(req) {
  const url = new URL(req.url ?? "/", upstream);
  url.pathname = rewriteObjectPathname(url.pathname);

  const headers = { ...req.headers };
  const uploadMetadata = headers["upload-metadata"];
  if (typeof uploadMetadata === "string") {
    const rewritten = rewriteUploadMetadataHeader(uploadMetadata);
    if (rewritten && rewritten !== uploadMetadata) {
      headers["upload-metadata"] = rewritten;
    }
  }

  delete headers.host;
  return { url, headers };
}

/** @param {import("node:http").IncomingMessage} req */
function proxy(req, res) {
  const { url, headers } = rewriteRequest(req);

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

const server = http.createServer(proxy);

server.listen(port, () => {
  console.log(`storage-path-proxy listening on :${port} -> ${upstream}`);
});
