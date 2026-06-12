import type { Express } from "express";
import { parseUpdatedSince } from "../lib/queryParams.js";
import type { RouteDeps } from "./deps.js";

export function registerCatalogRoutes(app: Express, deps: RouteDeps): void {
  app.get("/catalog/cycles", deps.optionalAuth, async (req, res) => {
    const cycles = await deps.catalog.getCycles(parseUpdatedSince(req.query));
    res.json({ cycles });
  });

  app.get("/catalog/music", deps.optionalAuth, async (req, res) => {
    const albums = await deps.catalog.getMusicAlbums(parseUpdatedSince(req.query));
    res.json({ albums });
  });

  app.get("/catalog/books/:bookId", deps.optionalAuth, async (req, res) => {
    const book = await deps.catalog.getBookDetail(req.params.bookId as string);
    if (!book) {
      res.status(404).json({ error: "Not found" });
      return;
    }
    res.json(book);
  });
}
