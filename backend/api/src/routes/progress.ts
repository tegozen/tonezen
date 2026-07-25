import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import type { RouteDeps } from "./deps.js";

const MAX_POSITION_MS = 24 * 60 * 60 * 1000 * 100; // 100 hours
const UPDATED_AT_SKEW_MS = 5 * 60 * 1000;

function parsePositionMs(value: unknown): number | null {
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n) || n < 0 || n > MAX_POSITION_MS) return null;
  return Math.floor(n);
}

function parseUpdatedAt(value: unknown, now = Date.now()): string | null {
  if (typeof value !== "string" || !value.trim()) return null;
  const ms = Date.parse(value);
  if (Number.isNaN(ms)) return null;
  const capped = Math.min(ms, now + UPDATED_AT_SKEW_MS);
  return new Date(capped).toISOString();
}

export function registerProgressRoutes(app: Express, deps: RouteDeps): void {
  app.get("/progress/audiobooks", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const progress = await deps.progress.getAudiobookProgress(req.user!.id);
    res.json({ progress });
  }));

  app.put("/progress/audiobooks/:bookId", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const { track_id, position_ms, updated_at } = req.body ?? {};
    if (!track_id || position_ms == null || !updated_at) {
      res.status(400).json({ error: "track_id, position_ms, updated_at required" });
      return;
    }
    const positionMs = parsePositionMs(position_ms);
    const updatedAt = parseUpdatedAt(updated_at);
    if (positionMs == null || !updatedAt) {
      res.status(400).json({ error: "Invalid position_ms or updated_at" });
      return;
    }
    const result = await deps.progress.upsertAudiobookProgress(
      req.user!.id,
      req.params.bookId as string,
      track_id,
      positionMs,
      updatedAt,
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
    res.json(result);
  }));
}
