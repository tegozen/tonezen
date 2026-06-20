import type { Express } from "express";
import { asyncRoute } from "../lib/http.js";
import type { RouteDeps } from "./deps.js";
import { normalizeInviteCode } from "../db/auth.js";

const MIN_PASSWORD_LENGTH = 6;

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
  app.post("/auth/invite/verify", asyncRoute(async (req, res) => {
    const code = normalizeInviteCode(String(req.body?.code ?? ""));
    const invite = await deps.auth.getInviteCode(code);
    if (!invite) {
      res.status(404).json({ error: "Invalid invite code" });
      return;
    }
    res.json({ valid: true });
  }));

  app.post("/auth/signup", asyncRoute(async (req, res) => {
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
      res.status(404).json({ error: "Invalid invite code" });
      return;
    }

    const created = await deps.authAdmin.createConfirmedUser({
      email,
      password,
      displayName,
    });
    if ("error" in created) {
      res.status(409).json({ error: "Email already registered" });
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

  app.post("/auth/password/recovery", asyncRoute(async (req, res) => {
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

  app.post("/auth/password/update", asyncRoute(async (req, res) => {
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
}
