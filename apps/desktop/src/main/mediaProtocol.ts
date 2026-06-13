import { net, protocol } from "electron";
import { pathToFileURL } from "node:url";
import { LOCAL_AUDIO_SCHEME, localAudioPathFromUrl } from "../shared/localAudioUrl.js";

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

export function setupLocalAudioProtocol(): void {
  protocol.handle(LOCAL_AUDIO_SCHEME, (request) => {
    const filePath = localAudioPathFromUrl(request.url);
    if (!filePath) {
      return new Response("Bad Request", { status: 400 });
    }
    return net.fetch(pathToFileURL(filePath).href);
  });
}
