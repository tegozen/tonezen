import type { RequestHandler } from "express";
import type pg from "pg";
import {
  AuthRepository,
  CatalogRepository,
  DownloadsRepository,
  ProgressRepository,
} from "../db/index.js";
import { authMiddleware, requireAuth } from "../middleware/auth.js";
import type { StorageSignConfig } from "../lib/storageSign.js";
import { AuthAdminClient, type AuthAdminConfig } from "../lib/authAdmin.js";

export interface RouteDeps {
  auth: AuthRepository;
  authAdmin: AuthAdminClient;
  catalog: CatalogRepository;
  downloads: DownloadsRepository;
  progress: ProgressRepository;
  optionalAuth: RequestHandler;
  requiredAuth: RequestHandler[];
  storage: StorageSignConfig;
}

export function createRouteDeps(
  pool: pg.Pool,
  jwtSecret: string,
  storage: StorageSignConfig,
  auth?: AuthAdminConfig,
): RouteDeps {
  const authConfig = auth ?? {
    authUrl: "http://auth:9999",
    publicBaseUrl: storage.publicBaseUrl,
    serviceRoleKey: storage.serviceRoleKey,
  };
  return {
    auth: new AuthRepository(pool),
    authAdmin: new AuthAdminClient(authConfig),
    catalog: new CatalogRepository(pool),
    downloads: new DownloadsRepository(pool),
    progress: new ProgressRepository(pool),
    storage,
    optionalAuth: authMiddleware(jwtSecret, true),
    requiredAuth: [authMiddleware(jwtSecret), requireAuth],
  };
}
