import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import type { WatchProvider } from "../db/bookWatch.js";
import type { RouteDeps } from "./deps.js";

const providers = new Set<WatchProvider>(["baza_knig", "allbookerka"]);

export function registerBookWatchRoutes(app: Express, deps: RouteDeps): void {
  app.get("/book-watch", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    res.json(await deps.bookWatch.snapshot(req.user!.id));
  }));
  app.put("/book-watch/watches/:watchId", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const body = req.body ?? {};
    const queries = Array.isArray(body.queries) ? body.queries : [];
    if (typeof body.display_title !== "string" || body.display_title.trim().length < 1 || body.display_title.trim().length > 200 ||
        typeof body.enabled !== "boolean" || queries.length > 40 || queries.some((query) =>
          !providers.has(query.provider) || typeof query.query !== "string" ||
          query.query.trim().length < 1 || query.query.trim().length > 200 || typeof query.enabled !== "boolean")) {
      res.status(400).json({ error: "Invalid watch" }); return;
    }
    const found = await deps.bookWatch.updateWatch(req.user!.id, req.params.watchId as string, {
      displayTitle: body.display_title, enabled: body.enabled, queries,
    });
    if (!found) { res.status(404).json({ error: "Watch not found" }); return; }
    res.json({ updated: true });
  }));
  app.post("/book-watch/checks", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    res.status(202).json({ job: await deps.bookWatch.enqueue(req.user!.id) });
  }));
  app.post("/book-watch/events/read", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const ids = Array.isArray(req.body?.event_ids) ? req.body.event_ids.filter((id: unknown) => typeof id === "string") : [];
    if (ids.length > 500) { res.status(400).json({ error: "Too many event_ids" }); return; }
    await deps.bookWatch.markRead(req.user!.id, ids);
    res.json({ updated: ids.length });
  }));
}
