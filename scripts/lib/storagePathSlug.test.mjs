import assert from "node:assert/strict";
import test from "node:test";
import {
  rewriteObjectPathname,
  rewriteUploadMetadataHeader,
  sanitizeStoragePath,
  transliterateRu,
} from "./storagePathSlug.mjs";

test("transliterateRu converts Cyrillic", () => {
  assert.equal(transliterateRu("Рыцарь системы"), "Rytsar sistemy");
  assert.equal(transliterateRu("Ёлка"), "Yolka");
});

test("sanitizeStoragePath transliterates audiobook layout", () => {
  assert.equal(
    sanitizeStoragePath("cycles/Рыцарь системы/1/01-01.mp3"),
    "cycles/rytsar-sistemy/1/01-01.mp3",
  );
  assert.equal(sanitizeStoragePath("music/Баста - Сансара.mp3"), "music/basta-sansara.mp3");
});

test("sanitizeStoragePath keeps already valid paths", () => {
  assert.equal(
    sanitizeStoragePath("cycles/horus-heresy/fallen-angels/001-intro.mp3"),
    "cycles/horus-heresy/fallen-angels/001-intro.mp3",
  );
});

test("rewriteUploadMetadataHeader updates objectName metadata", () => {
  const objectName = Buffer.from("cycles/Рыцарь системы/1/01-01.mp3", "utf8").toString("base64");
  const header = `bucketName Y29udGVudA==,objectName ${objectName},contentType YXVkaW8vbXBlZzM=`;

  const rewritten = rewriteUploadMetadataHeader(header);
  const objectPair = rewritten.split(",").find((part) => part.startsWith("objectName "));
  assert.ok(objectPair);

  const encoded = objectPair.slice("objectName ".length);
  assert.equal(
    Buffer.from(encoded, "base64").toString("utf8"),
    "cycles/rytsar-sistemy/1/01-01.mp3",
  );
});

test("rewriteObjectPathname updates encoded object URLs", () => {
  const pathname = rewriteObjectPathname(
    "/object/content/cycles/" + encodeURIComponent("Рыцарь системы") + "/1/01-01.mp3",
  );
  assert.equal(pathname, "/object/content/cycles/rytsar-sistemy/1/01-01.mp3");
});
