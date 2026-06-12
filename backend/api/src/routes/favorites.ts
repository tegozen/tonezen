import type { Express } from "express";
import type { RouteDeps } from "./deps.js";

export function registerFavoriteRoutes(app: Express, deps: RouteDeps): void {
  app.get("/favorites", ...deps.requiredAuth, async (req, res) => {
    const favorites = await deps.repo.getFavorites(req.user!.id);
    res.json({ favorites });
  });

  app.post("/favorites", ...deps.requiredAuth, async (req, res) => {
    const bookId = req.body?.book_id as string | undefined;
    if (!bookId) {
      res.status(400).json({ error: "book_id required" });
      return;
    }
    await deps.repo.addFavorite(req.user!.id, bookId);
    res.status(201).json({ ok: true });
  });

  app.delete("/favorites/:bookId", ...deps.requiredAuth, async (req, res) => {
    await deps.repo.removeFavorite(req.user!.id, req.params.bookId as string);
    res.status(204).send();
  });
}
