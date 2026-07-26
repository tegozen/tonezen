/** Build GlitchTip / Sentry DSN for Tonezen clients (path-prefixed self-host). */

/** Fixed GlitchTip project ids (must match docker/glitchtip-seed/seed.py). */
export const GLITCHTIP_ANDROID_PROJECT_ID = 1;
export const GLITCHTIP_DESKTOP_PROJECT_ID = 2;

export function buildGlitchtipDsn(
  baseUrl: string,
  publicKey: string,
  projectId: string | number,
): string {
  const key = publicKey.trim().replace(/-/g, "");
  const id = String(projectId).trim();
  if (!baseUrl.trim() || !key || !id) return "";

  let url: URL;
  try {
    url = new URL(baseUrl.trim());
  } catch {
    return "";
  }

  const host = url.port ? `${url.hostname}:${url.port}` : url.hostname;
  return `${url.protocol}//${key}@${host}/glitchtip/${id}`;
}
