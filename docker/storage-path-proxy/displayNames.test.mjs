import assert from "node:assert/strict";
import test from "node:test";
import { upsertContentDisplayName } from "./displayNames.mjs";

test("upsertContentDisplayName writes mapping through PostgREST", async () => {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url, init });
    return { ok: true, text: async () => "" };
  };

  await upsertContentDisplayName(
    {
      postgrestUrl: "http://rest:3000",
      serviceRoleKey: "service-key",
    },
    {
      storagePath: "cycles/rytsar-sistemy/kniga-1/01-glava.mp3",
      displayPath: "cycles/Рыцарь системы/Книга 1/01 глава.mp3",
    },
    fetchImpl,
  );

  assert.equal(calls.length, 1);
  assert.equal(
    calls[0].url,
    "http://rest:3000/content_display_names?on_conflict=storage_path",
  );
  assert.equal(calls[0].init.method, "POST");
  assert.equal(calls[0].init.headers.Authorization, "Bearer service-key");
  assert.equal(calls[0].init.headers.apikey, "service-key");
  assert.equal(calls[0].init.headers.Prefer, "resolution=merge-duplicates");
  const body = JSON.parse(calls[0].init.body);
  assert.equal(body.storage_path, "cycles/rytsar-sistemy/kniga-1/01-glava.mp3");
  assert.equal(body.display_path, "cycles/Рыцарь системы/Книга 1/01 глава.mp3");
  assert.equal(Number.isNaN(Date.parse(body.updated_at)), false);
  assert.deepEqual(Object.keys(body).sort(), [
    "display_path",
    "storage_path",
    "updated_at",
  ]);
});

test("upsertContentDisplayName skips missing or unchanged mappings", async () => {
  const fetchImpl = async () => {
    throw new Error("fetch should not be called");
  };

  await upsertContentDisplayName(
    { postgrestUrl: "http://rest:3000", serviceRoleKey: "service-key" },
    null,
    fetchImpl,
  );
  await upsertContentDisplayName(
    { postgrestUrl: "http://rest:3000", serviceRoleKey: "service-key" },
    {
      storagePath: "cycles/saga/book/01.mp3",
      displayPath: "cycles/saga/book/01.mp3",
    },
    fetchImpl,
  );
});

test("upsertContentDisplayName reports PostgREST errors", async () => {
  const fetchImpl = async () => ({
    ok: false,
    status: 500,
    text: async () => "db down",
  });

  await assert.rejects(
    upsertContentDisplayName(
      { postgrestUrl: "http://rest:3000", serviceRoleKey: "service-key" },
      {
        storagePath: "cycles/rytsar-sistemy/kniga-1/01-glava.mp3",
        displayPath: "cycles/Рыцарь системы/Книга 1/01 глава.mp3",
      },
      fetchImpl,
    ),
    /Display name mapping upsert failed \(500\): db down/,
  );
});
