import fs from "node:fs";
import path from "node:path";

/** Avoid Readable.toWeb double-close (nodejs/node#54205) when piping file streams to Response. */
function nodeStreamToWeb(resultStream: fs.ReadStream): ReadableStream<Uint8Array> {
  resultStream.pause();
  let closed = false;

  return new ReadableStream({
    start(controller) {
      resultStream.on("data", (chunk: string | Buffer) => {
        if (closed) return;
        controller.enqueue(Buffer.isBuffer(chunk) ? new Uint8Array(chunk) : new TextEncoder().encode(chunk));
        if ((controller.desiredSize ?? 0) <= 0) resultStream.pause();
      });
      resultStream.on("error", (error) => {
        controller.error(error);
      });
      resultStream.on("end", () => {
        if (closed) return;
        closed = true;
        controller.close();
      });
    },
    pull() {
      if (!closed) resultStream.resume();
    },
    cancel() {
      if (closed) return;
      closed = true;
      resultStream.destroy();
    },
  });
}

function mimeForAudioPath(filePath: string): string {
  switch (path.extname(filePath).toLowerCase()) {
    case ".mp3":
      return "audio/mpeg";
    case ".m4a":
    case ".mp4":
      return "audio/mp4";
    case ".ogg":
      return "audio/ogg";
    case ".flac":
      return "audio/flac";
    case ".wav":
      return "audio/wav";
    default:
      return "application/octet-stream";
  }
}

/** Parse a single HTTP bytes range; end is inclusive. */
export function parseBytesRange(
  rangeHeader: string,
  size: number,
): { start: number; end: number } | null {
  if (!rangeHeader.startsWith("bytes=") || size <= 0) return null;
  const spec = rangeHeader.slice("bytes=".length).split(",")[0]?.trim();
  if (!spec) return null;
  const dash = spec.indexOf("-");
  if (dash < 0) return null;
  const startText = spec.slice(0, dash);
  const endText = spec.slice(dash + 1);

  let start: number;
  let end: number;
  if (startText === "") {
    const suffix = Number(endText);
    if (!Number.isFinite(suffix) || suffix <= 0) return null;
    start = Math.max(0, size - suffix);
    end = size - 1;
  } else {
    start = Number(startText);
    end = endText === "" ? size - 1 : Number(endText);
    if (!Number.isFinite(start) || !Number.isFinite(end)) return null;
  }

  if (start < 0 || end < start || start >= size) return null;
  return { start, end: Math.min(end, size - 1) };
}

/**
 * Serve a local audio file with Accept-Ranges / 206 Partial Content so
 * HTMLMediaElement seeking works under protocol.handle (electron#38749).
 */
export function createLocalAudioResponse(filePath: string, request: Request): Response {
  const stat = fs.statSync(filePath);
  const mime = mimeForAudioPath(filePath);
  const rangeHeader = request.headers.get("Range");

  if (!rangeHeader) {
    const stream = fs.createReadStream(filePath);
    return new Response(nodeStreamToWeb(stream), {
      status: 200,
      headers: {
        "Accept-Ranges": "bytes",
        "Content-Type": mime,
        "Content-Length": String(stat.size),
      },
    });
  }

  const range = parseBytesRange(rangeHeader, stat.size);
  if (!range) {
    return new Response("Range Not Satisfiable", {
      status: 416,
      headers: {
        "Content-Range": `bytes */${stat.size}`,
      },
    });
  }

  const { start, end } = range;
  const stream = fs.createReadStream(filePath, { start, end });
  return new Response(nodeStreamToWeb(stream), {
    status: 206,
    headers: {
      "Accept-Ranges": "bytes",
      "Content-Type": mime,
      "Content-Length": String(end - start + 1),
      "Content-Range": `bytes ${start}-${end}/${stat.size}`,
    },
  });
}
