import http from "node:http";

export interface IndexerHealthState {
  lastSuccessAt: number | null;
  lastFailureAt: number | null;
}

function isReady(state: IndexerHealthState, maxAgeMs: number): boolean {
  const { lastSuccessAt, lastFailureAt } = state;
  if (lastSuccessAt == null) {
    return false;
  }

  const now = Date.now();
  return (
    now - lastSuccessAt <= maxAgeMs && (lastFailureAt == null || lastSuccessAt >= lastFailureAt)
  );
}

export function createHealthServer(
  port: number,
  maxAgeMs: number,
  getState: () => IndexerHealthState,
): http.Server {
  return http.createServer((req, res) => {
    res.setHeader("Content-Type", "application/json");

    if (req.url === "/health") {
      res.statusCode = 200;
      res.end(JSON.stringify({ status: "ok" }));
      return;
    }

    if (req.url === "/ready") {
      const ready = isReady(getState(), maxAgeMs);
      res.statusCode = ready ? 200 : 503;
      res.end(JSON.stringify({ status: ready ? "ok" : "not_ready" }));
      return;
    }

    res.statusCode = 404;
    res.end();
  }).listen(port, "0.0.0.0", () => {
    console.log(`[indexer] health on :${port}`);
  });
}
