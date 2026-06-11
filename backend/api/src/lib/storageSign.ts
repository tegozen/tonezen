export interface StorageSignConfig {
  storageUrl: string;
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
  return data.signedURL;
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
