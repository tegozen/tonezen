import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AudiobookProgress } from "../src/shared/types.js";

type StoredProgress = AudiobookProgress & { pendingSync: boolean };

const dbState = vi.hoisted(() => ({
  progress: null as StoredProgress | null,
}));

vi.mock("../src/main/database.js", () => ({
  LocalDatabase: {
    upsertProgress: vi.fn((progress: AudiobookProgress, pendingSync: boolean) => {
      dbState.progress = { ...progress, pendingSync };
    }),
    getProgress: vi.fn(() => dbState.progress),
    getPendingProgress: vi.fn(() => []),
    getPendingSyncCount: vi.fn(() => (dbState.progress?.pendingSync ? 1 : 0)),
    getLastSyncAtEpochMs: vi.fn(() => null),
    markProgressSynced: vi.fn((bookId: string) => {
      if (dbState.progress?.bookId === bookId) {
        dbState.progress = { ...dbState.progress, pendingSync: false };
      }
    }),
    setLastSyncAtEpochMs: vi.fn(),
  },
}));

describe("ProgressSyncService", () => {
  beforeEach(() => {
    dbState.progress = null;
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it("applies server winner returned by progress push", async () => {
    const { ProgressSyncService } = await import("../src/main/progressSync.js");
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          skipped: true,
          progress: {
            book_id: "book-1",
            track_id: "track-newer",
            position_ms: 42_000,
            updated_at: "2999-06-01T00:00:00Z",
          },
        }),
      }),
    );
    const service = new ProgressSyncService(
      () => "token",
      async () => undefined,
      () => true,
      { baseUrl: "https://tonezen.test", anonKey: "anon" },
    );

    await service.saveLocal("book-1", "track-old", 10_000);

    expect(dbState.progress).toEqual({
      bookId: "book-1",
      trackId: "track-newer",
      positionMs: 42_000,
      updatedAt: "2999-06-01T00:00:00Z",
      pendingSync: false,
    });
  });
});
