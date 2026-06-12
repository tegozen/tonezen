import type { RequestHandler } from "express";
import type pg from "pg";
import {
  CatalogRepository,
  DownloadsRepository,
  FavoritesRepository,
  ProgressRepository,
} from "../db/index.js";
import { authMiddleware, requireAuth } from "../middleware/auth.js";
import type { StorageSignConfig } from "../lib/storageSign.js";

export interface RouteDeps {
  catalog: CatalogRepository;
  downloads: DownloadsRepository;
  favorites: FavoritesRepository;
  progress: ProgressRepository;
  optionalAuth: RequestHandler;
  requiredAuth: RequestHandler[];
  storage: StorageSignConfig;
}

export function createRouteDeps(
  pool: pg.Pool,
  jwtSecret: string,
  storage: StorageSignConfig,
): RouteDeps {
  return {
    catalog: new CatalogRepository(pool),
    downloads: new DownloadsRepository(pool),
    favorites: new FavoritesRepository(pool),
    progress: new ProgressRepository(pool),
    storage,
    optionalAuth: authMiddleware(jwtSecret, true),
    requiredAuth: [authMiddleware(jwtSecret), requireAuth],
  };
}
