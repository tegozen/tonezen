import express from "express";
import cors from "cors";
import helmet from "helmet";
import pg from "pg";
import type { StorageSignConfig } from "./lib/storageSign.js";
import type { AuthAdminConfig } from "./lib/authAdmin.js";
import { asyncRoute, healthHandler, registerErrorHandler } from "./lib/http.js";
import { createRouteDeps } from "./routes/deps.js";
import { registerAuthRoutes } from "./routes/auth.js";
import { registerCatalogRoutes } from "./routes/catalog.js";
import { registerDownloadRoutes } from "./routes/downloads.js";
import { registerProgressRoutes } from "./routes/progress.js";
import { registerBookWatchRoutes } from "./routes/bookWatch.js";

export interface AppConfig {
  jwtSecret: string;
  jwtAudience?: string;
  jwtIssuer?: string;
  corsOrigins?: string[];
  storage: StorageSignConfig;
  auth?: AuthAdminConfig;
}

function resolveCorsOrigin(config: AppConfig): boolean | string[] {
  if (config.corsOrigins && config.corsOrigins.length > 0) {
    return config.corsOrigins;
  }
  const fromEnv = (process.env.CORS_ORIGINS ?? "")
    .split(",")
    .map((origin) => origin.trim())
    .filter(Boolean);
  if (fromEnv.length > 0) return fromEnv;
  // Default: allow the public base URL only (desktop/Android use native HTTP, not CORS).
  const base = process.env.TONEZEN_BASE_URL?.replace(/\/$/, "");
  return base ? [base] : false;
}

export function createApp(pool: pg.Pool, config: AppConfig) {
  const app = express();
  const deps = createRouteDeps(
    pool,
    config.jwtSecret,
    config.storage,
    config.auth,
    { audience: config.jwtAudience, issuer: config.jwtIssuer },
  );

  app.use(helmet({ contentSecurityPolicy: false }));
  app.use(
    cors({
      origin: resolveCorsOrigin(config),
      credentials: false,
    }),
  );
  app.use(express.json({ limit: "32kb" }));

  app.get("/health", asyncRoute((req, res) => healthHandler(pool, req, res)));

  registerAuthRoutes(app, deps);
  registerCatalogRoutes(app, deps);
  registerDownloadRoutes(app, deps);
  registerProgressRoutes(app, deps);
  registerBookWatchRoutes(app, deps);

  registerErrorHandler(app);

  return app;
}
