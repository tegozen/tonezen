import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import { parseUpdatedSince } from "../lib/queryParams.js";
import type { RouteDeps } from "./deps.js";

export function registerCatalogRoutes(app: Express, deps: RouteDeps): void {
  app.get("/catalog/cycles", deps.optionalAuth, asyncRoute(async (req, res) => {
    const updatedSince = parseUpdatedSince(req.query);
    if (updatedSince === false) {
      res.status(400).json({ error: "Invalid updated_since" });
      return;
    }
    const cycles = await deps.catalog.getCycles(updatedSince);
    res.json({ cycles });
  }));

  app.get("/catalog/music", deps.optionalAuth, asyncRoute(async (req, res) => {
    const updatedSince = parseUpdatedSince(req.query);
    if (updatedSince === false) {
      res.status(400).json({ error: "Invalid updated_since" });
      return;
    }
    const albums = await deps.catalog.getMusicAlbums(updatedSince);
    res.json({ albums });
  }));

  app.get("/catalog/books/:bookId", deps.optionalAuth, asyncRoute(async (req, res) => {
    const book = await deps.catalog.getBookDetail(req.params.bookId as string);
    if (!book) {
      res.status(404).json({ error: "Not found" });
      return;
    }
    res.json(book);
  }));

  app.get("/catalog/tracks", deps.optionalAuth, asyncRoute(async (req, res) => {
    const updatedSince = parseUpdatedSince(req.query);
    if (updatedSince === false) {
      res.status(400).json({ error: "Invalid updated_since" });
      return;
    }
    const tracks = await deps.catalog.getAllTracks(updatedSince);
    res.json({ tracks });
  }));
}
