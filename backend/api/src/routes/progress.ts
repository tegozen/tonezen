import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import { parseBaseRevision } from "../lib/progressCas.js";
import type { RouteDeps } from "./deps.js";

const MAX_POSITION_MS = 24 * 60 * 60 * 1000 * 100; // 100 hours

function parsePositionMs(value: unknown): number | null {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n) || n < 0 || n > MAX_POSITION_MS) return null;
  return Math.floor(n);
}

export function registerProgressRoutes(app: Express, deps: RouteDeps): void {
  app.get("/progress/audiobooks", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const progress = await deps.progress.getAudiobookProgress(req.user!.id);
    res.json({ progress });
  }));

  app.put("/progress/audiobooks/:bookId", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const body = req.body ?? {};
    const { track_id, position_ms, base_revision } = body;
    if (base_revision === undefined || base_revision === null) {
      res.status(400).json({ error: "base_revision required" });
      return;
    }
    if (!track_id || position_ms == null) {
      res.status(400).json({ error: "track_id, position_ms, base_revision required" });
      return;
    }
    const positionMs = parsePositionMs(position_ms);
    const baseRevision = parseBaseRevision(base_revision);
    if (positionMs == null || baseRevision == null) {
      res.status(400).json({ error: "Invalid position_ms or base_revision" });
      return;
    }
    const result = await deps.progress.upsertAudiobookProgress(
      req.user!.id,
      req.params.bookId as string,
      track_id,
      positionMs,
      baseRevision,
    );
    if ("error" in result && result.error === "not_found") {
      res.status(404).json({ error: "Book not found" });
      return;
    }
    if ("error" in result && result.error === "not_audiobook") {
      res.status(400).json({ error: "Progress sync only for audiobooks" });
      return;
    }
    if ("error" in result && result.error === "invalid_track") {
      res.status(400).json({ error: "track_id does not belong to book" });
      return;
    }
    if ("conflict" in result && result.conflict) {
      res.status(409).json({
        error: "cas_conflict",
        progress: result.progress,
      });
      return;
    }
    if ("error" in result && result.error === "cas_conflict") {
      res.status(409).json({ error: "cas_conflict" });
      return;
    }
    res.json(result);
  }));
}
