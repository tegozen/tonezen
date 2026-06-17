import type { Express } from "express";
import { signStoragePaths } from "../lib/storageSign.js";
import { asyncRoute } from "../lib/http.js";
import type { RouteDeps } from "./deps.js";

const MAX_SIGN_TRACK_IDS = 100;

export function registerDownloadRoutes(app: Express, deps: RouteDeps): void {
  app.post("/downloads/sign", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const trackIds = req.body?.track_ids as string[] | undefined;
    if (!Array.isArray(trackIds) || trackIds.length === 0) {
      res.status(400).json({ error: "track_ids required" });
      return;
    }
    if (trackIds.length > MAX_SIGN_TRACK_IDS) {
      res.status(400).json({ error: `track_ids limited to ${MAX_SIGN_TRACK_IDS}` });
      return;
    }
    const rows = await deps.downloads.getTrackStoragePaths(trackIds);
    try {
      const signed = await signStoragePaths(
        rows.map((row) => row.storage_path),
        deps.storage,
      );
      const urls = rows.map((row) => ({
        track_id: row.track_id,
        url: signed.get(row.storage_path) ?? null,
      }));
      if (urls.some((entry) => !entry.url)) {
        res.status(502).json({ error: "Failed to sign one or more download URLs" });
        return;
      }
      res.json({ urls });
    } catch (err) {
      console.error("[api] storage sign error:", err);
      res.status(502).json({ error: "Storage sign failed" });
    }
  }));
}
