import { QueryClient } from "@tanstack/react-query";

/** Defaults for local IPC reads (SQLite via preload) — not browser network. */
export function createAppQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        networkMode: "always",
        retry: false,
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
      mutations: {
        networkMode: "always",
        retry: false,
      },
    },
  });
}
