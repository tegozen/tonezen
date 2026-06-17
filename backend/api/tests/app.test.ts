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
      publicBaseUrl: "http://localhost:8000",
      bucket: "content",
      serviceRoleKey: "service-role-key",
      expiresIn: 900,
    },
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("GET /health returns ok", async () => {
    vi.mocked(mockPool.query).mockResolvedValueOnce({ rows: [{ "?column?": 1 }] } as never);
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

  it("POST /downloads/sign rejects oversized track_ids", async () => {
    const res = await request(app)
      .post("/downloads/sign")
      .set("Authorization", `Bearer ${makeToken("user-1")}`)
      .send({ track_ids: Array.from({ length: 101 }, (_, i) => `id-${i}`) });

    expect(res.status).toBe(400);
  });

  it("GET /catalog/cycles rejects invalid updated_since", async () => {
    const res = await request(app).get("/catalog/cycles?updated_since=not-a-date");
    expect(res.status).toBe(400);
  });

  it("GET /catalog/cycles returns 500 when db fails", async () => {
    vi.mocked(mockPool.query).mockRejectedValueOnce(new Error("db down"));
    const res = await request(app).get("/catalog/cycles");
    expect(res.status).toBe(500);
    expect(res.body.error).toBe("Internal server error");
  });

  it("rejects JWT signed with none algorithm", async () => {
    const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
    const payload = Buffer.from(JSON.stringify({ sub: "user-1", role: "authenticated" })).toString(
      "base64url",
    );
    const token = `${header}.${payload}.`;

    const res = await request(app)
      .post("/downloads/sign")
      .set("Authorization", `Bearer ${token}`)
      .send({ track_ids: ["t1"] });

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

  it("PUT /progress/audiobooks rejects track not in book", async () => {
    vi.mocked(mockPool.query)
      .mockResolvedValueOnce({ rows: [{ content_type: "audiobook" }] } as never)
      .mockResolvedValueOnce({ rows: [] } as never);

    const res = await request(app)
      .put("/progress/audiobooks/book-1")
      .set("Authorization", `Bearer ${makeToken("user-1")}`)
      .send({
        track_id: "t-other",
        position_ms: 100,
        updated_at: new Date().toISOString(),
      });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe("track_id does not belong to book");
  });
});
