import rateLimit, { ipKeyGenerator } from "express-rate-limit";
import type { RequestHandler } from "express";

const isTest = process.env.NODE_ENV === "test" || process.env.VITEST === "true";

function jsonRateLimitHandler(
  _req: Parameters<RequestHandler>[0],
  res: Parameters<RequestHandler>[1],
): void {
  res.status(429).json({ error: "Too many requests" });
}

/** Strict per-IP limit for unauthenticated auth endpoints. */
export const authRateLimiter: RequestHandler = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: isTest ? 10_000 : 30,
  standardHeaders: true,
  legacyHeaders: false,
  handler: jsonRateLimitHandler,
});

/** Per-user limit for signed download URL minting. */
export const downloadsSignRateLimiter: RequestHandler = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: isTest ? 10_000 : 120,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => {
    if (req.user?.id) return `user:${req.user.id}`;
    return ipKeyGenerator(req.ip ?? "anonymous");
  },
  handler: jsonRateLimitHandler,
});
