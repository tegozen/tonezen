import { afterEach, describe, expect, it, vi } from "vitest";
import { CatalogSyncService } from "../src/main/catalogSync.js";

describe("CatalogSyncService HTTP handling", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("rejects catalog cycle fetch when the API returns an error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: "unavailable" }), {
          status: 503,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    const service = new CatalogSyncService("https://tonezen.test", () => "token");

    await expect(service.fetchCycles()).rejects.toThrow("Catalog request failed (503)");
  });

  it("maps valid waveform peaks from book tracks and drops invalid arrays", async () => {
    const waveformPeaks = Array.from({ length: 64 }, (_, index) => index);
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          tracks: [
            {
              id: "t1",
              sort_order: 0,
              title: "Intro",
              filename: "001.mp3",
              duration_ms: 60000,
              waveform_peaks: waveformPeaks,
            },
            {
              id: "t2",
              sort_order: 1,
              title: "Broken",
              filename: "002.mp3",
              duration_ms: 61000,
              waveform_peaks: [0, 100],
            },
          ],
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    const service = new CatalogSyncService("https://tonezen.test", () => "token");

    const tracks = await service.fetchBookTracks("b1");

    expect(fetchMock).toHaveBeenCalledWith(
      "https://tonezen.test/api/v1/catalog/books/b1",
      { headers: { Authorization: "Bearer token" } },
    );
    expect(tracks).toEqual([
      {
        id: "t1",
        bookId: "b1",
        sortOrder: 0,
        title: "Intro",
        filename: "001.mp3",
        artist: undefined,
        durationMs: 60000,
        waveformPeaks,
      },
      {
        id: "t2",
        bookId: "b1",
        sortOrder: 1,
        title: "Broken",
        filename: "002.mp3",
        artist: undefined,
        durationMs: 61000,
        waveformPeaks: undefined,
      },
    ]);
  });
});
