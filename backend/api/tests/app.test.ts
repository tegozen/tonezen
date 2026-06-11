import jwt from "jsonwebtoken";
import request from "supertest";
import pg from "pg";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { createApp } from "../src/app.js";
import * as storageSign from "../src/lib/storageSign.js";

const JWT_SECRET = "test-jwt-secret-at-least-32-characters-long";

function makeToken(userId: string): string {
  return jwt.sign({ sub: userId, role: "authenticated" }, JWT_SECRET, { expiresIn: "1h" });
}

describe("API routes", () => {
  const mockPool = {
    query: vi.fn(),
  } as unknown as pg.Pool;

  const app = createApp(mockPool, {
    jwtSecret: JWT_SECRET,
    storage: {
      storageUrl: "http://storage:5000",
      bucket: "content",
      serviceRoleKey: "service-role-key",
      expiresIn: 900,
    },
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("GET /health returns ok", async () => {
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("ok");
  });

  it("GET /catalog/cycles returns cycles", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({ rows: [] } as never);
    const res = await request(app).get("/catalog/cycles");
    expect(res.status).toBe(200);
    expect(res.body.cycles).toEqual([]);
  });

  it("POST /downloads/sign requires auth", async () => {
    const res = await request(app).post("/downloads/sign").send({ track_ids: [] });
    expect(res.status).toBe(401);
  });

  it("POST /downloads/sign returns storage signed urls", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({
      rows: [{ track_id: "t1", storage_path: "music/a/audio/1.mp3" }],
    } as never);
    vi.spyOn(storageSign, "signStoragePaths").mockResolvedValue(
      new Map([["music/a/audio/1.mp3", "http://localhost:8000/storage/v1/object/sign/content/music/a/audio/1.mp3?token=x"]]),
    );

    const res = await request(app)
      .post("/downloads/sign")
      .set("Authorization", `Bearer ${makeToken("user-1")}`)
      .send({ track_ids: ["t1"] });

    expect(res.status).toBe(200);
    expect(res.body.urls).toHaveLength(1);
    expect(res.body.urls[0].url).toContain("/storage/v1/object/sign/content/");
  });

  it("PUT /progress/audiobooks rejects music content", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({
      rows: [{ content_type: "music" }],
    } as never);

    const res = await request(app)
      .put("/progress/audiobooks/book-1")
      .set("Authorization", `Bearer ${makeToken("user-1")}`)
      .send({
        track_id: "t1",
        position_ms: 100,
        updated_at: new Date().toISOString(),
      });

    expect(res.status).toBe(400);
  });
});
