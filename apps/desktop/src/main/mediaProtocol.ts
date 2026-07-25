import fs from "node:fs";
import { net, protocol } from "electron";
import path from "node:path";
import { pathToFileURL } from "node:url";
import { LOCAL_AUDIO_SCHEME, localAudioPathFromUrl } from "../shared/localAudioUrl.js";
import { isPathUnderRoot, sanitizeLocalAudioPath } from "../shared/safeLocalPaths.js";

let allowedAudioRoots: string[] = [];

export function registerLocalAudioScheme(): void {
  protocol.registerSchemesAsPrivileged([
    {
      scheme: LOCAL_AUDIO_SCHEME,
      privileges: {
        standard: true,
        secure: true,
        supportFetchAPI: true,
        stream: true,
        corsEnabled: true,
        bypassCSP: true,
      },
    },
  ]);
}

function resolveSafeAudioPath(rawPath: string): string | null {
  const filePath = sanitizeLocalAudioPath(rawPath, allowedAudioRoots);
  if (!filePath) return null;
  try {
    const real = fs.realpathSync(filePath);
    for (const root of allowedAudioRoots) {
      const realRoot = fs.existsSync(root) ? fs.realpathSync(root) : path.resolve(root);
      if (isPathUnderRoot(realRoot, real)) return real;
    }
    return null;
  } catch {
    return null;
  }
}

export function setupLocalAudioProtocol(allowedRoots: readonly string[]): void {
  allowedAudioRoots = allowedRoots.map((root) => path.resolve(root));
  protocol.handle(LOCAL_AUDIO_SCHEME, (request) => {
    const rawPath = localAudioPathFromUrl(request.url);
    if (!rawPath) {
      return new Response("Bad Request", { status: 400 });
    }
    const filePath = resolveSafeAudioPath(rawPath);
    if (!filePath) {
      return new Response("Forbidden", { status: 403 });
    }
    return net.fetch(pathToFileURL(filePath).href);
  });
}
