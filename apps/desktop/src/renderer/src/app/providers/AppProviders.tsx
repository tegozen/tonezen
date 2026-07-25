import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createAppQueryClient } from "./queryClient";

const queryClient = createAppQueryClient();

export function AppProviders({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

export { queryClient };
