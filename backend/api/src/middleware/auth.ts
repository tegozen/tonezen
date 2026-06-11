import type { Request, Response, NextFunction } from "express";
import jwt from "jsonwebtoken";

export interface AuthUser {
  id: string;
  role: string;
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser;
    }
  }
}

export function authMiddleware(jwtSecret: string, optional = false) {
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
      const payload = jwt.verify(token, jwtSecret) as jwt.JwtPayload;
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
