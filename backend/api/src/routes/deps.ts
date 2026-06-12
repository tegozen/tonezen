import type { RequestHandler } from "express";
import type { ApiRepository } from "../db.js";
import { authMiddleware, requireAuth } from "../middleware/auth.js";
import type { StorageSignConfig } from "../lib/storageSign.js";

export interface RouteDeps {
  repo: ApiRepository;
  optionalAuth: RequestHandler;
  requiredAuth: RequestHandler[];
  storage: StorageSignConfig;
}

export function createRouteDeps(
  repo: ApiRepository,
  jwtSecret: string,
  storage: StorageSignConfig,
): RouteDeps {
  return {
    repo,
    storage,
    optionalAuth: authMiddleware(jwtSecret, true),
    requiredAuth: [authMiddleware(jwtSecret), requireAuth],
  };
}
