import type pg from "pg";
import type { Express, RequestHandler } from "express";

type AsyncRequestHandler = (
  req: Parameters<RequestHandler>[0],
  res: Parameters<RequestHandler>[1],
  next: Parameters<RequestHandler>[2],
) => Promise<void>;

export function asyncRoute(handler: AsyncRequestHandler): RequestHandler {
  return (req, res, next) => {
    void handler(req, res, next).catch(next);
  };
}

export function registerErrorHandler(app: Express): void {
  app.use((err: unknown, _req, res, next) => {
    console.error("[api] unhandled error:", err);
    if (res.headersSent) return;
    void next;
    res.status(500).json({ error: "Internal server error" });
  });
}

export async function healthHandler(pool: pg.Pool, _req: unknown, res: { status: (code: number) => { json: (body: unknown) => void } }): Promise<void> {
  await pool.query("SELECT 1");
  res.status(200).json({ status: "ok" });
}
