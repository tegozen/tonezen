import assert from "node:assert/strict";
import test from "node:test";
import { isReadRequest, rewriteRequest } from "./server.mjs";

/** @param {{ method: string, url: string, headers?: Record<string, string> }} init */
function fakeReq(init) {
  return { method: init.method, url: init.url, headers: init.headers ?? {} };
}

test("isReadRequest covers GET/HEAD/OPTIONS only", () => {
  assert.equal(isReadRequest("GET"), true);
  assert.equal(isReadRequest("HEAD"), true);
  assert.equal(isReadRequest("OPTIONS"), true);
  assert.equal(isReadRequest("POST"), false);
  assert.equal(isReadRequest("PUT"), false);
  assert.equal(isReadRequest("PATCH"), false);
});

test("read of a mixed-case object key is forwarded verbatim (no lowercasing)", () => {
  // Signed-download GET: the token was signed for this exact key, and the stored
  // object key is mixed-case. The proxy must not alter it, or storage-api returns
  // InvalidSignature.
  const { url, mapping } = rewriteRequest(
    fakeReq({
      method: "GET",
      url: "/object/sign/content/music/Anacondaz_Vse_khorosho_feat_Inice.m4a?token=abc",
    }),
  );

  assert.equal(
    url.pathname,
    "/object/sign/content/music/Anacondaz_Vse_khorosho_feat_Inice.m4a",
  );
  assert.equal(url.search, "?token=abc");
  assert.equal(mapping, null);
});

test("read of a canonical (already lowercase) key is unchanged", () => {
  const { url, mapping } = rewriteRequest(
    fakeReq({
      method: "GET",
      url: "/object/sign/content/cycles/antidemon/2/02-04.mp3?token=xyz",
    }),
  );

  assert.equal(url.pathname, "/object/sign/content/cycles/antidemon/2/02-04.mp3");
  assert.equal(mapping, null);
});

test("upload with Cyrillic key is still sanitized to an ASCII storage key", () => {
  const { url, mapping } = rewriteRequest(
    fakeReq({
      method: "PUT",
      url: `/object/content/music/${encodeURIComponent("Песня.mp3")}`,
    }),
  );

  assert.equal(url.pathname, "/object/content/music/pesnya.mp3");
  assert.deepEqual(mapping, {
    storagePath: "music/pesnya.mp3",
    displayPath: "music/Песня.mp3",
  });
});
