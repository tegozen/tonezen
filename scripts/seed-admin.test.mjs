import assert from "node:assert/strict";
import test from "node:test";
import {
  buildAdminUserPayload,
  createAdminUser,
  ensureAdminUser,
  findUserByEmail,
} from "./seed-admin.mjs";

test("buildAdminUserPayload confirms email and sets admin metadata", () => {
  const payload = buildAdminUserPayload("admin@tonezen.local", "secret");
  assert.equal(payload.email, "admin@tonezen.local");
  assert.equal(payload.password, "secret");
  assert.equal(payload.email_confirm, true);
  assert.deepEqual(payload.user_metadata, { role: "admin" });
});

test("findUserByEmail returns matching user", async () => {
  const fetchFn = async () => ({
    ok: true,
    async json() {
      return {
        users: [
          { id: "1", email: "other@x.local" },
          { id: "2", email: "admin@tonezen.local" },
        ],
      };
    },
  });

  const user = await findUserByEmail({
    authUrl: "http://auth:9999",
    serviceRoleKey: "service-key",
    email: "admin@tonezen.local",
    fetchFn,
  });

  assert.equal(user.id, "2");
});

test("ensureAdminUser skips create when admin already exists", async () => {
  let createCalled = false;
  const fetchFn = async (url, init) => {
    if (url.endsWith("/health")) return { ok: true };
    if (url.includes("/admin/users?page=")) {
      return {
        ok: true,
        async json() {
          return { users: [{ id: "existing", email: "admin@tonezen.local" }] };
        },
      };
    }
    if (init?.method === "POST") createCalled = true;
    return { ok: true, async json() { return {}; } };
  };

  const result = await ensureAdminUser({
    authUrl: "http://auth:9999",
    serviceRoleKey: "service-key",
    email: "admin@tonezen.local",
    password: "secret",
    fetchFn,
    waitFn: async () => {},
  });

  assert.equal(result.created, false);
  assert.equal(result.userId, "existing");
  assert.equal(createCalled, false);
});

test("ensureAdminUser creates admin when missing", async () => {
  const fetchFn = async (url, init) => {
    if (url.endsWith("/health")) return { ok: true };
    if (url.includes("/admin/users?page=")) {
      return { ok: true, async json() { return { users: [] }; } };
    }
    if (init?.method === "POST") {
      return { ok: true, async json() { return { id: "new-admin" }; } };
    }
    return { ok: false, async text() { return "unexpected"; } };
  };

  const result = await ensureAdminUser({
    authUrl: "http://auth:9999",
    serviceRoleKey: "service-key",
    email: "admin@tonezen.local",
    password: "secret",
    fetchFn,
    waitFn: async () => {},
  });

  assert.equal(result.created, true);
  assert.equal(result.userId, "new-admin");
});

test("createAdminUser throws on failure", async () => {
  const fetchFn = async () => ({
    ok: false,
    status: 500,
    async text() {
      return "boom";
    },
  });

  await assert.rejects(
    () =>
      createAdminUser({
        authUrl: "http://auth:9999",
        serviceRoleKey: "service-key",
        email: "admin@tonezen.local",
        password: "secret",
        fetchFn,
      }),
    /Create admin failed \(500\): boom/,
  );
});
