import express from "express";
import cors from "cors";
import pg from "pg";
import { ApiRepository } from "./db.js";
import { authMiddleware, requireAuth } from "./middleware/auth.js";
import { signDownloadUrl } from "./lib/crypto.js";

export interface AppConfig {
  jwtSecret: string;
  downloadUrlSecret: string;
  downloadUrlTtlSeconds: number;
  downloadBaseUrl: string;
}

export function createApp(pool: pg.Pool, config: AppConfig) {
  const app = express();
  const repo = new ApiRepository(pool);

  app.use(cors());
  app.use(express.json());

  const optionalAuth = authMiddleware(config.jwtSecret, true);
  const requiredAuth = [authMiddleware(config.jwtSecret), requireAuth];

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  app.get("/catalog/cycles", optionalAuth, async (req, res) => {
    const updatedSince = req.query.updated_since as string | undefined;
    const cycles = await repo.getCycles(updatedSince);
    res.json({ cycles });
  });

  app.get("/catalog/music", optionalAuth, async (req, res) => {
    const updatedSince = req.query.updated_since as string | undefined;
    const albums = await repo.getMusicAlbums(updatedSince);
    res.json({ albums });
  });

  app.get("/catalog/books/:bookId", optionalAuth, async (req, res) => {
    const book = await repo.getBookDetail(req.params.bookId);
    if (!book) {
      res.status(404).json({ error: "Not found" });
      return;
    }
    res.json(book);
  });

  app.post("/downloads/sign", ...requiredAuth, async (req, res) => {
    const trackIds = req.body?.track_ids as string[] | undefined;
    if (!Array.isArray(trackIds) || trackIds.length === 0) {
      res.status(400).json({ error: "track_ids required" });
      return;
    }
    const rows = await repo.getTrackStoragePaths(trackIds);
    const urls = rows.map((row) => ({
      track_id: row.track_id,
      url: signDownloadUrl(
        row.storage_path,
        config.downloadUrlSecret,
        config.downloadUrlTtlSeconds,
        config.downloadBaseUrl,
      ),
    }));
    res.json({ urls });
  });

  app.get("/favorites", ...requiredAuth, async (req, res) => {
    const favorites = await repo.getFavorites(req.user!.id);
    res.json({ favorites });
  });

  app.post("/favorites", ...requiredAuth, async (req, res) => {
    const bookId = req.body?.book_id as string | undefined;
    if (!bookId) {
      res.status(400).json({ error: "book_id required" });
      return;
    }
    await repo.addFavorite(req.user!.id, bookId);
    res.status(201).json({ ok: true });
  });

  app.delete("/favorites/:bookId", ...requiredAuth, async (req, res) => {
    await repo.removeFavorite(req.user!.id, req.params.bookId);
    res.status(204).send();
  });

  app.get("/progress/audiobooks", ...requiredAuth, async (req, res) => {
    const progress = await repo.getAudiobookProgress(req.user!.id);
    res.json({ progress });
  });

  app.put("/progress/audiobooks/:bookId", ...requiredAuth, async (req, res) => {
    const { track_id, position_ms, updated_at } = req.body ?? {};
    if (!track_id || position_ms == null || !updated_at) {
      res.status(400).json({ error: "track_id, position_ms, updated_at required" });
      return;
    }
    const result = await repo.upsertAudiobookProgress(
      req.user!.id,
      req.params.bookId,
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

  return app;
}
