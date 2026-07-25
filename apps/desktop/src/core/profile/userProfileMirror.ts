import type { AuthConfig } from "@core/auth/supabaseAuth.js";

export interface UserProfileMirrorRow {
  user_id: string;
  display_name: string | null;
  avatar_url: string | null;
  updated_at: string;
}

export async function upsertUserProfileMirror(
  config: AuthConfig,
  accessToken: string,
  row: UserProfileMirrorRow,
): Promise<void> {
  const url = `${config.baseUrl.replace(/\/$/, "")}/rest/v1/user_profiles?on_conflict=user_id`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      apikey: config.anonKey,
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=minimal",
    },
    body: JSON.stringify(row),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Profile mirror upsert failed (${response.status}): ${text}`);
  }
}
