export interface StorageSignConfig {
  storageUrl: string;
  publicBaseUrl: string;
  bucket: string;
  serviceRoleKey: string;
  expiresIn: number;
}

interface SignResponse {
  signedURL?: string;
}

/** Sign one object path via Supabase Storage API (service role). */
export async function signStoragePath(
  path: string,
  config: StorageSignConfig,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  const encodedPath = path
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  const url = `${config.storageUrl}/object/sign/${config.bucket}/${encodedPath}`;
  const res = await fetchImpl(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.serviceRoleKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ expiresIn: config.expiresIn }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Storage sign failed (${res.status}): ${body}`);
  }
  const data = (await res.json()) as SignResponse;
  if (!data.signedURL) {
    throw new Error("Storage sign response missing signedURL");
  }
  return toPublicDownloadUrl(data.signedURL, config.publicBaseUrl);
}

/** Storage returns relative paths (/object/sign/...); clients need absolute Kong URLs. */
export function toPublicDownloadUrl(signedURL: string, publicBaseUrl: string): string {
  const base = publicBaseUrl.replace(/\/$/, "");
  if (signedURL.startsWith("http://") || signedURL.startsWith("https://")) {
    return signedURL;
  }
  if (signedURL.startsWith("/storage/v1/")) {
    return `${base}${signedURL}`;
  }
  if (signedURL.startsWith("/object/")) {
    return `${base}/storage/v1${signedURL}`;
  }
  if (signedURL.startsWith("/")) {
    return `${base}${signedURL}`;
  }
  return signedURL;
}

export async function signStoragePaths(
  paths: string[],
  config: StorageSignConfig,
  fetchImpl: typeof fetch = fetch,
): Promise<Map<string, string>> {
  const uniquePaths = [...new Set(paths)];
  const entries = await Promise.all(
    uniquePaths.map(async (path) => [path, await signStoragePath(path, config, fetchImpl)] as const),
  );
  return new Map(entries);
}
