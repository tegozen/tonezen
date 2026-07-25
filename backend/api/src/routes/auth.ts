import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import type { RouteDeps } from "./deps.js";
import { normalizeInviteCode } from "../db/auth.js";

const MIN_PASSWORD_LENGTH = 12;

function normalizeEmail(email: unknown): string {
  return typeof email === "string" ? email.trim().toLowerCase() : "";
}

function normalizeDisplayName(displayName: unknown): string {
  return typeof displayName === "string" ? displayName.trim() : "";
}

function isValidPassword(password: unknown): password is string {
  return typeof password === "string" && password.length >= MIN_PASSWORD_LENGTH;
}

export function registerAuthRoutes(app: Express, deps: RouteDeps): void {
  app.post("/auth/invite/verify", deps.authRateLimiter, asyncRoute(async (req, res) => {
    const code = normalizeInviteCode(String(req.body?.code ?? ""));
    const invite = code ? await deps.auth.getInviteCode(code) : null;
    res.json({ valid: Boolean(invite) });
  }));

  app.post("/auth/signup", deps.authRateLimiter, asyncRoute(async (req, res) => {
    const inviteCode = normalizeInviteCode(String(req.body?.invite_code ?? ""));
    const email = normalizeEmail(req.body?.email);
    const password = req.body?.password;
    const displayName = normalizeDisplayName(req.body?.display_name);

    if (!inviteCode || !email || !isValidPassword(password)) {
      res.status(400).json({ error: "invite_code, email and password required" });
      return;
    }

    const invite = await deps.auth.getInviteCode(inviteCode);
    if (!invite) {
      // Same status as other signup failures to avoid invite-code oracle via signup.
      res.status(400).json({ error: "Signup failed" });
      return;
    }

    const created = await deps.authAdmin.createConfirmedUser({
      email,
      password,
      displayName,
    });
    if ("error" in created) {
      res.status(400).json({ error: "Signup failed" });
      return;
    }

    await deps.auth.ensureReferralCode(created.id);
    await deps.auth.createRedemption(created.id, invite.owner_user_id, invite.code);
    res.status(201).json({ user: { id: created.id, email: created.email } });
  }));

  app.get("/auth/referral-code", ...deps.requiredAuth, asyncRoute(async (req, res) => {
    const code = await deps.auth.ensureReferralCode(req.user!.id);
    res.json({ code });
  }));

  app.post("/auth/password/recovery", deps.authRateLimiter, asyncRoute(async (req, res) => {
    const email = normalizeEmail(req.body?.email);
    if (email) {
      try {
        await deps.authAdmin.sendPasswordRecovery(email);
      } catch (err) {
        console.error("[api] password recovery request failed:", err);
      }
    }
    res.json({ sent: true });
  }));

  app.post("/auth/password/update", deps.authRateLimiter, asyncRoute(async (req, res) => {
    const accessToken = typeof req.body?.access_token === "string" ? req.body.access_token : "";
    const password = req.body?.password;
    if (!accessToken || !isValidPassword(password)) {
      res.status(400).json({ error: "access_token and password required" });
      return;
    }
    const result = await deps.authAdmin.updatePasswordWithRecoveryToken(accessToken, password);
    if (result === "invalid_token") {
      res.status(401).json({ error: "Invalid recovery token" });
      return;
    }
    res.json({ updated: true });
  }));

  app.post(
    "/auth/password",
    ...deps.requiredAuth,
    deps.authRateLimiter,
    asyncRoute(async (req, res) => {
      const currentPassword =
        typeof req.body?.current_password === "string" ? req.body.current_password : "";
      const password = req.body?.password;
      if (!currentPassword || !isValidPassword(password)) {
        res.status(400).json({ error: "current_password and password required" });
        return;
      }
      const header = req.headers.authorization ?? "";
      const accessToken = header.startsWith("Bearer ") ? header.slice(7) : "";
      if (!accessToken) {
        res.status(401).json({ error: "Authentication required" });
        return;
      }
      const result = await deps.authAdmin.changePasswordWithCurrentPassword({
        accessToken,
        currentPassword,
        newPassword: password,
      });
      if (result === "invalid_token") {
        res.status(401).json({ error: "Authentication required" });
        return;
      }
      if (result === "wrong_password") {
        res.status(401).json({ error: "Current password is incorrect" });
        return;
      }
      res.json({ updated: true });
    }),
  );
}
