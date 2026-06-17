import http from "node:http";

export interface IndexerHealthState {
  lastSuccessAt: number | null;
  lastFailureAt: number | null;
}

export function createHealthServer(
  port: number,
  maxAgeMs: number,
  getState: () => IndexerHealthState,
): http.Server {
  return http.createServer((req, res) => {
    if (req.url !== "/health") {
      res.statusCode = 404;
      res.end();
      return;
    }

    const { lastSuccessAt, lastFailureAt } = getState();
    const now = Date.now();
    const healthy =
      lastSuccessAt != null &&
      now - lastSuccessAt <= maxAgeMs &&
      (lastFailureAt == null || lastSuccessAt >= lastFailureAt);

    res.statusCode = healthy ? 200 : 503;
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ status: healthy ? "ok" : "unhealthy" }));
  }).listen(port, "0.0.0.0", () => {
    console.log(`[indexer] health on :${port}`);
  });
}
