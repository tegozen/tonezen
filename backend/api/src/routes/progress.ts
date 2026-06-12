import type { Express } from "express";
import type { RouteDeps } from "./deps.js";

export function registerProgressRoutes(app: Express, deps: RouteDeps): void {
  app.get("/progress/audiobooks", ...deps.requiredAuth, async (req, res) => {
    const progress = await deps.progress.getAudiobookProgress(req.user!.id);
    res.json({ progress });
  });

  app.put("/progress/audiobooks/:bookId", ...deps.requiredAuth, async (req, res) => {
    const { track_id, position_ms, updated_at } = req.body ?? {};
    if (!track_id || position_ms == null || !updated_at) {
      res.status(400).json({ error: "track_id, position_ms, updated_at required" });
      return;
    }
    const result = await deps.progress.upsertAudiobookProgress(
      req.user!.id,
      req.params.bookId as string,
      track_id,
      Number(position_ms),
      updated_at,
    );
    if ("error" in result && result.error === "not_found") {
      res.status(404).json({ error: "Book not found" });
      return;
    }
    if ("error" in result && result.error === "not_audiobook") {
      res.status(400).json({ error: "Progress sync only for audiobooks" });
      return;
    }
    res.json(result);
  });
}
