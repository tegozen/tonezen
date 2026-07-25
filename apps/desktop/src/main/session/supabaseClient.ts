import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import WebSocket from "ws";

export function createSupabaseClient(baseUrl: string, anonKey: string): SupabaseClient {
  return createClient(baseUrl, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    realtime: {
      // Node.js < 22 (Electron main) has no native WebSocket — ws is required for Realtime.
      transport: WebSocket as unknown as typeof globalThis.WebSocket,
    },
  });
}
