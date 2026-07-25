import type { TonezenApi } from "@core/ipc/tonezenApi";

/** Typed access to the preload bridge. UI must go through slice api segments eventually. */
export function getTonezenApi(): TonezenApi {
  return window.tonezen;
}

export type { TonezenApi };
