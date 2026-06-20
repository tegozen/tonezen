import jwt from "jsonwebtoken";
import request from "supertest";
import pg from "pg";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";

const JWT_SECRET = "test-jwt-secret-at-least-32-characters-long";

function makeToken(userId: string): string {
  return jwt.sign({ sub: userId, role: "authenticated" }, JWT_SECRET, { expiresIn: "1h" });
}

function createTestApp(pool: pg.Pool) {
  return createApp(pool, {
    jwtSecret: JWT_SECRET,
    auth: {
      authUrl: "http://auth:9999",
      publicBaseUrl: "http://localhost:8000",
      serviceRoleKey: "service-role-key",
    },
    storage: {
      storageUrl: "http://storage:5000",
      publicBaseUrl: "http://localhost:8000",
      bucket: "content",
      serviceRoleKey: "service-role-key",
      expiresIn: 900,
    },
  });
}

describe("auth invite routes", () => {
  const mockPool = {
    query: vi.fn(),
  } as unknown as pg.Pool;
  const app = createTestApp(mockPool);

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("blocks signup with an invalid invite code", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({ rows: [] } as never);

    const res = await request(app).post("/auth/signup").send({
      invite_code: "bad-code",
      email: "new@example.com",
      password: "secret123",
      display_name: "New User",
    });

    expect(res.status).toBe(404);
    expect(res.body.error).toBe("Invalid invite code");
    expect(vi.mocked(mockPool.query)).toHaveBeenCalledTimes(1);
  });

  it("creates a user, redemption, and invitee referral code for a valid invite code", async () => {
    vi.mocked(mockPool.query)
      .mockResolvedValueOnce({
        rows: [{ code: "ABCD1234EFGH", owner_user_id: "inviter-1" }],
      } as never)
      .mockResolvedValueOnce({ rows: [] } as never)
      .mockResolvedValueOnce({ rows: [{ code: "NEWWUSER1234" }] } as never)
      .mockResolvedValueOnce({ rows: [] } as never);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: "invitee-1", email: "new@example.com" }),
      }),
    );

    const res = await request(app).post("/auth/signup").send({
      invite_code: " abcd-1234-efgh ",
      email: " NEW@example.com ",
      password: "secret123",
      display_name: " New User ",
    });

    expect(res.status).toBe(201);
    expect(res.body.user).toEqual({ id: "invitee-1", email: "new@example.com" });
    expect(fetch).toHaveBeenCalledWith(
      "http://auth:9999/admin/users",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({
          email: "new@example.com",
          password: "secret123",
          email_confirm: true,
          user_metadata: { full_name: "New User" },
        }),
      }),
    );
    expect(vi.mocked(mockPool.query)).toHaveBeenCalledTimes(4);
  });

  it("returns conflict for duplicate email without creating a redemption", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({
      rows: [{ code: "ABCD1234EFGH", owner_user_id: "inviter-1" }],
    } as never);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 422,
        text: async () => "User already registered",
      }),
    );

    const res = await request(app).post("/auth/signup").send({
      invite_code: "ABCD1234EFGH",
      email: "used@example.com",
      password: "secret123",
    });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe("Email already registered");
    expect(vi.mocked(mockPool.query)).toHaveBeenCalledTimes(1);
  });

  it("returns or creates the current user's referral code", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({ rows: [{ code: "CURRENT12345" }] } as never);

    const res = await request(app)
      .get("/auth/referral-code")
      .set("Authorization", `Bearer ${makeToken("user-1")}`);

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ code: "CURRENT12345" });
  });

  it("requires auth for the current user's referral code", async () => {
    const res = await request(app).get("/auth/referral-code");

    expect(res.status).toBe(401);
  });
});

describe("password recovery routes", () => {
  const mockPool = {
    query: vi.fn(),
  } as unknown as pg.Pool;
  const app = createTestApp(mockPool);

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("returns generic success even when GoTrue rejects recovery", async () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        text: async () => "email not found",
      }),
    );

    const res = await request(app)
      .post("/auth/password/recovery")
      .send({ email: "missing@example.com" });

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ sent: true });
  });

  it("rejects password update without a recovery token", async () => {
    const res = await request(app)
      .post("/auth/password/update")
      .send({ password: "secret123" });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe("access_token and password required");
  });

  it("updates password with a valid recovery token", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: "user-1" }),
      }),
    );

    const res = await request(app)
      .post("/auth/password/update")
      .send({ access_token: "recovery-token", password: "secret123" });

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ updated: true });
    expect(fetch).toHaveBeenCalledWith(
      "http://auth:9999/user",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ password: "secret123" }),
      }),
    );
  });

  it("rejects invalid recovery tokens", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        text: async () => "invalid token",
      }),
    );

    const res = await request(app)
      .post("/auth/password/update")
      .send({ access_token: "bad-token", password: "secret123" });

    expect(res.status).toBe(401);
    expect(res.body.error).toBe("Invalid recovery token");
  });
});
