import type { Express } from "express";
import type { RouteDeps } from "./deps.js";

function parseUpdatedSince(query: Record<string, unknown>): string | undefined {
  const value = query.updated_since;
  return typeof value === "string" ? value : undefined;
}

export function registerCatalogRoutes(app: Express, deps: RouteDeps): void {
  app.get("/catalog/cycles", deps.optionalAuth, async (req, res) => {
    const cycles = await deps.repo.getCycles(parseUpdatedSince(req.query));
    res.json({ cycles });
  });

  app.get("/catalog/music", deps.optionalAuth, async (req, res) => {
    const albums = await deps.repo.getMusicAlbums(parseUpdatedSince(req.query));
    res.json({ albums });
  });

  app.get("/catalog/books/:bookId", deps.optionalAuth, async (req, res) => {
    const book = await deps.repo.getBookDetail(req.params.bookId as string);
    if (!book) {
      res.status(404).json({ error: "Not found" });
      return;
    }
    res.json(book);
  });
}
