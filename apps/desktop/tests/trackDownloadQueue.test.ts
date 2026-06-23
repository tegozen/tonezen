import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, expect, it, vi } from "vitest";
import type { DownloadManager, ResumableDownloadOutcome } from "../src/main/downloadManager.js";
import type { SessionService } from "../src/main/sessionService.js";
import { TrackDownloadQueue } from "../src/main/trackDownloadQueue.js";
import { resolveTrackDownloadPath } from "../src/shared/safeLocalPaths.js";

const db = vi.hoisted(() => {
  type QueueRow = {
    bookId: string;
    trackId: string;
    priority: string;
    batchId: string | null;
    enqueuedAt: number;
    title: string;
    subtitle: string | null;
    contentType: string;
    status: string;
    bytesDownloaded: number;
    totalBytes: number | null;
    tempPath: string | null;
  };
  type TrackRow = {
    id: string;
    bookId: string;
    localPath?: string | null;
  };

  const queueRows = new Map<string, QueueRow>();
  const tracks = new Map<string, TrackRow>();
  const key = (bookId: string, trackId: string) => `${bookId}\0${trackId}`;

  return {
    reset() {
      queueRows.clear();
      tracks.clear();
    },
    addTrack(track: TrackRow) {
      tracks.set(track.id, { ...track });
    },
    LocalDatabase: {
      resolveLocalTrackPath(bookId: string, trackId: string, _downloadsRoot: string): string | null {
        const track = tracks.get(trackId);
        return track?.bookId === bookId && track.localPath ? track.localPath : null;
      },
      get(bookId: string, trackId: string): QueueRow | null {
        return queueRows.get(key(bookId, trackId)) ?? null;
      },
      getAll(): QueueRow[] {
        return Array.from(queueRows.values());
      },
      upsert(item: QueueRow): void {
        queueRows.set(key(item.bookId, item.trackId), { ...item });
      },
      delete(bookId: string, trackId: string): void {
        queueRows.delete(key(bookId, trackId));
      },
      deleteAll(): void {
        queueRows.clear();
      },
      updateProgress(
        bookId: string,
        trackId: string,
        bytesDownloaded: number,
        totalBytes: number | null,
        tempPath: string | null,
      ): void {
        const item = queueRows.get(key(bookId, trackId));
        if (!item) return;
        queueRows.set(key(bookId, trackId), {
          ...item,
          bytesDownloaded,
          totalBytes,
          tempPath,
        });
      },
      markTrackDownloaded(bookId: string, trackId: string, localPath: string): boolean {
        const track = tracks.get(trackId);
        if (!track || track.bookId !== bookId) return false;
        tracks.set(trackId, { ...track, localPath });
        return true;
      },
    },
  };
});

vi.mock("../src/main/database.js", () => ({
  LocalDatabase: db.LocalDatabase,
}));

function setupCatalog(downloadsRoot: string) {
  db.reset();
  db.addTrack({ id: "track-1", bookId: "book-1" });
  db.addTrack({ id: "track-2", bookId: "book-1" });
  fs.mkdirSync(downloadsRoot, { recursive: true });
}

function createSessionService(): SessionService {
  return {
    isOnline: () => true,
    refreshIfNeeded: vi.fn(async () => "AuthenticatedOnline"),
    getAccessToken: () => "token",
  } as unknown as SessionService;
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitFor(predicate: () => boolean, timeoutMs = 1_000): Promise<boolean> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    if (predicate()) return true;
    await wait(20);
  }
  return predicate();
}

async function withTimeout<T>(promise: Promise<T>, timeoutMs = 1_000): Promise<T | "TIMEOUT"> {
  return Promise.race([
    promise,
    wait(timeoutMs).then(() => "TIMEOUT" as const),
  ]);
}

describe("TrackDownloadQueue", () => {
  it("drops repeatedly failed downloads so the queue can continue", async () => {
    const userData = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-queue-db-"));
    const downloadsRoot = path.join(userData, "downloads");
    setupCatalog(downloadsRoot);

    const attempts: string[] = [];
    const downloadManager = {
      downloadTrackResumable: vi.fn(
        async (
          bookId: string,
          trackId: string,
          _bytesAlreadyDownloaded: number,
          _totalBytesHint: number | null,
          onProgress: (progress: number) => void,
        ): Promise<ResumableDownloadOutcome> => {
          attempts.push(trackId);
          if (trackId === "track-1") {
            await wait(5);
            throw new Error("transfer failed");
          }

          const finalPath = resolveTrackDownloadPath(downloadsRoot, bookId, trackId);
          if (!finalPath) throw new Error("invalid path");
          fs.mkdirSync(path.dirname(finalPath), { recursive: true });
          fs.writeFileSync(finalPath, "audio");
          onProgress(1);
          return { finalPath, bytesDownloaded: 5, totalBytes: 5 };
        },
      ),
      cancelActiveDownload: vi.fn(),
      deleteLocalTrack: vi.fn(async () => {}),
    } as unknown as DownloadManager;

    const logDownloadFailure = vi.fn();
    const queue = new TrackDownloadQueue(
      downloadsRoot,
      downloadManager,
      createSessionService(),
      logDownloadFailure,
    );

    try {
      const failedTrack = queue.awaitTrack("book-1", "track-1", {
        priority: "PLAY",
        title: "001",
        subtitle: "Book",
        contentType: "audiobook",
      });
      queue.enqueue({
        bookId: "book-1",
        trackId: "track-2",
        priority: "BULK",
        title: "002",
        subtitle: "Book",
        contentType: "audiobook",
      });

      await expect(withTimeout(failedTrack)).resolves.toBe("FAILED");
      expect(await waitFor(() => db.LocalDatabase.get("book-1", "track-2") == null)).toBe(true);
      expect(db.LocalDatabase.resolveLocalTrackPath("book-1", "track-2", downloadsRoot)).not.toBeNull();
      expect(db.LocalDatabase.get("book-1", "track-1")).toBeNull();
      expect(attempts.filter((trackId) => trackId === "track-1")).toHaveLength(3);
      expect(attempts).toContain("track-2");
      expect(logDownloadFailure).toHaveBeenCalledWith(
        expect.objectContaining({
          area: "download",
          code: "FAILED",
          bookId: "book-1",
          trackId: "track-1",
          trackTitle: "001",
          bookTitle: "Book",
        }),
      );
    } finally {
      await queue.cancelAll();
    }
  });
});
