import { normalizeDownloadUrl } from "@core/platform/safeLocalPaths.js";
import { apiV1Url } from "@core/platform/serverPaths.js";

export async function signedUrlForTrack(
  baseUrl: string,
  getAccessToken: () => string | null,
  trackId: string,
): Promise<string> {
  const token = getAccessToken();
  if (!token) throw new Error("__download_auth_required__");

  const response = await fetch(apiV1Url(baseUrl, "/downloads/sign"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ track_ids: [trackId] }),
  });
  if (!response.ok) throw new Error("__download_sign_failed__");
  const json = (await response.json()) as { urls: Array<{ track_id: string; url: string }> };
  const signed = json.urls.find((u) => u.track_id === trackId);
  if (!signed) throw new Error("__download_no_signed_url__");
  return signed.url;
}

export function resolveDownloadUrl(baseUrl: string, signedUrl: string): string {
  const apiBase = baseUrl.replace(/\/$/, "");
  let absolute = signedUrl;
  if (!signedUrl.startsWith("http://") && !signedUrl.startsWith("https://")) {
    if (signedUrl.startsWith("/storage/v1/")) {
      absolute = `${apiBase}${signedUrl}`;
    } else if (signedUrl.startsWith("/")) {
      absolute = `${apiBase}/storage/v1${signedUrl}`;
    }
  }
  try {
    const target = new URL(absolute);
    const allowed = new URL(apiBase);
    if (target.hostname === "localhost" || target.hostname === "127.0.0.1") {
      const port = target.port || allowed.port;
      target.hostname = allowed.hostname;
      if (port) target.port = port;
      return target.toString();
    }
  } catch {
    return absolute;
  }
  return normalizeDownloadUrl(absolute, apiBase);
}
