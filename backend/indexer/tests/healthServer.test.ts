import { describe, expect, it } from "vitest";
import { createHealthServer, type IndexerHealthState } from "../src/healthServer.js";

async function listenPort(server: ReturnType<typeof createHealthServer>): Promise<number> {
  await new Promise<void>((resolve) => {
    if (server.listening) {
      resolve();
      return;
    }
    server.once("listening", () => resolve());
  });
  const address = server.address();
  if (!address || typeof address === "string") {
    throw new Error("expected TCP address");
  }
  return address.port;
}

describe("indexer health server", () => {
  it("returns 503 before first successful run", async () => {
    const state: IndexerHealthState = { lastSuccessAt: null, lastFailureAt: null };
    const server = createHealthServer(0, 60_000, () => state);
    const port = await listenPort(server);

    const res = await fetch(`http://127.0.0.1:${port}/health`);
    expect(res.status).toBe(503);

    await new Promise<void>((resolve, reject) => {
      server.close((err) => (err ? reject(err) : resolve()));
    });
  });

  it("returns 200 after successful run within max age", async () => {
    const state: IndexerHealthState = { lastSuccessAt: Date.now(), lastFailureAt: null };
    const server = createHealthServer(0, 60_000, () => state);
    const port = await listenPort(server);

    const res = await fetch(`http://127.0.0.1:${port}/health`);
    expect(res.status).toBe(200);

    await new Promise<void>((resolve, reject) => {
      server.close((err) => (err ? reject(err) : resolve()));
    });
  });
});
