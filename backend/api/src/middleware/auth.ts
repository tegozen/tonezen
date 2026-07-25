import type { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";

export interface AuthUser {
  id: string;
  role: string;
}

export interface JwtVerifyOptions {
  audience?: string;
  issuer?: string;
}

declare module "express-serve-static-core" {
  interface Request {
    user?: AuthUser;
  }
}

export function authMiddleware(
  jwtSecret: string,
  optional = false,
  verifyOptions: JwtVerifyOptions = {},
) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const header = req.headers.authorization;
    if (!header?.startsWith("Bearer ")) {
      if (optional) {
        next();
        return;
      }
      res.status(401).json({ error: "Missing authorization" });
      return;
    }
    const token = header.slice(7);
    try {
      const options: jwt.VerifyOptions = { algorithms: ["HS256"] };
      if (verifyOptions.audience) options.audience = verifyOptions.audience;
      if (verifyOptions.issuer) options.issuer = verifyOptions.issuer;
      const payload = jwt.verify(token, jwtSecret, options) as jwt.JwtPayload;
      const sub = payload.sub;
      if (!sub) {
        res.status(401).json({ error: "Invalid token" });
        return;
      }
      req.user = { id: sub, role: String(payload.role ?? "authenticated") };
      next();
    } catch {
      if (optional) {
        next();
        return;
      }
      res.status(401).json({ error: "Invalid token" });
    }
  };
}

export function requireAuth(req: Request, res: Response, next: NextFunction): void {
  if (!req.user) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }
  next();
}
