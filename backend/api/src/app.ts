import express from "express";
import cors from "cors";
import pg from "pg";
import type { StorageSignConfig } from "./lib/storageSign.js";
import { asyncRoute, healthHandler, registerErrorHandler } from "./lib/http.js";
import { createRouteDeps } from "./routes/deps.js";
import { registerCatalogRoutes } from "./routes/catalog.js";
import { registerDownloadRoutes } from "./routes/downloads.js";
import { registerProgressRoutes } from "./routes/progress.js";

export interface AppConfig {
  jwtSecret: string;
  storage: StorageSignConfig;
}

export function createApp(pool: pg.Pool, config: AppConfig) {
  const app = express();
  const deps = createRouteDeps(pool, config.jwtSecret, config.storage);

  app.use(cors());
  app.use(express.json());

  app.get("/health", asyncRoute((req, res) => healthHandler(pool, req, res)));

  registerCatalogRoutes(app, deps);
  registerDownloadRoutes(app, deps);
  registerProgressRoutes(app, deps);

  registerErrorHandler(app);

  return app;
}
