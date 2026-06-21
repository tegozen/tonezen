import { beforeEach, describe, expect, it, vi } from "vitest";
import type { StoredSession } from "../src/shared/types.js";

const realtimeMock = vi.hoisted(() => ({
  eventCallbacks: [] as Array<() => void>,
  subscribeCallbacks: [] as Array<(status: string, err?: unknown) => void>,
  setAuth: vi.fn(),
  unsubscribe: vi.fn(),
  removeAllChannels: vi.fn(),
}));

vi.mock("../src/main/supabaseClient.js", () => ({
  createSupabaseClient: vi.fn(() => ({
    realtime: { setAuth: realtimeMock.setAuth },
    removeAllChannels: realtimeMock.removeAllChannels,
    channel: vi.fn(() => {
      const channel = {
        on: vi.fn((_event: string, _filter: unknown, callback: () => void) => {
          realtimeMock.eventCallbacks.push(callback);
          return channel;
        }),
        subscribe: vi.fn((callback: (status: string, err?: unknown) => void) => {
          realtimeMock.subscribeCallbacks.push(callback);
          return channel;
        }),
        unsubscribe: realtimeMock.unsubscribe,
      };
      return channel;
    }),
  })),
}));

const session: StoredSession = {
  userId: "user-1",
  email: "user@example.test",
  displayName: "User",
  accessToken: "token",
  refreshToken: "refresh",
  expiresAtEpochSeconds: 4_102_444_800,
};

describe("isAuthSubscriptionError", () => {
  it("detects expired JWT realtime errors", async () => {
    const { isAuthSubscriptionError } = await import("../src/main/catalogRealtimeSync.js");
    const err = new Error("Realtime error", {
      cause: { reason: "Token has expired 143 seconds ago" },
    });
    expect(isAuthSubscriptionError(err)).toBe(true);
  });

  it("ignores unrelated channel errors", async () => {
    const { isAuthSubscriptionError } = await import("../src/main/catalogRealtimeSync.js");
    expect(isAuthSubscriptionError(new Error("connection closed"))).toBe(false);
  });
});

describe("CatalogRealtimeSyncService", () => {
  beforeEach(() => {
    realtimeMock.eventCallbacks = [];
    realtimeMock.subscribeCallbacks = [];
    realtimeMock.setAuth.mockClear();
    realtimeMock.unsubscribe.mockClear();
    realtimeMock.removeAllChannels.mockClear();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it("retries catalog subscription after transient realtime timeout", async () => {
    vi.useFakeTimers();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const consoleInfo = vi.spyOn(console, "info").mockImplementation(() => {});
    const { CatalogRealtimeSyncService } = await import("../src/main/catalogRealtimeSync.js");
    const syncCatalog = vi.fn().mockResolvedValue([]);
    const refreshSession = vi.fn().mockResolvedValue(undefined);
    try {
      const service = new CatalogRealtimeSyncService(
        { syncCatalog } as never,
        { baseUrl: "https://tonezen.test", anonKey: "anon" },
        () => "token",
        refreshSession,
        () => true,
      );

      await service.start(session);
      expect(realtimeMock.subscribeCallbacks).toHaveLength(1);

      realtimeMock.subscribeCallbacks[0]("TIMED_OUT", new Error("join timeout"));
      await vi.advanceTimersByTimeAsync(2_000);

      expect(realtimeMock.subscribeCallbacks).toHaveLength(2);
      realtimeMock.subscribeCallbacks[1]("SUBSCRIBED");
      realtimeMock.eventCallbacks.at(-1)?.();
      await vi.advanceTimersByTimeAsync(2_000);

      expect(syncCatalog).toHaveBeenCalledTimes(1);
    } finally {
      consoleError.mockRestore();
      consoleInfo.mockRestore();
      vi.useRealTimers();
    }
  });
});
