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
});
