import type { TonezenApi } from "@core/ipc/tonezenApi";

declare global {
  interface Window {
    tonezen: TonezenApi;
  }
}

export {};
