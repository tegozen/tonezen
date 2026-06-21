/**
 * @typedef {{ storagePath: string, displayPath: string }} DisplayPathMapping
 * @typedef {{ postgrestUrl: string, serviceRoleKey: string }} DisplayNameStoreConfig
 */

/**
 * @param {DisplayNameStoreConfig} config
 * @param {DisplayPathMapping | null} mapping
 * @param {typeof fetch} fetchImpl
 */
export async function upsertContentDisplayName(config, mapping, fetchImpl = fetch) {
  if (!mapping || mapping.storagePath === mapping.displayPath) {
    return;
  }
  if (!config.postgrestUrl || !config.serviceRoleKey) {
    throw new Error("content display name store is not configured");
  }

  const url = `${config.postgrestUrl.replace(/\/$/, "")}/content_display_names?on_conflict=storage_path`;
  const response = await fetchImpl(url, {
    method: "POST",
    headers: {
      apikey: config.serviceRoleKey,
      Authorization: `Bearer ${config.serviceRoleKey}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates",
    },
    body: JSON.stringify({
      storage_path: mapping.storagePath,
      display_path: mapping.displayPath,
      updated_at: new Date().toISOString(),
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Display name mapping upsert failed (${response.status}): ${body}`);
  }
}
