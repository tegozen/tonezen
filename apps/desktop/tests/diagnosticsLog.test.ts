import { describe, expect, it } from "vitest";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { appendDiagnosticError } from "../src/main/diagnosticsLog.js";

describe("appendDiagnosticError", () => {
  it("writes download errors to a JSON lines log under the user's Documents folder", async () => {
    const documentsPath = fs.mkdtempSync(path.join(os.tmpdir(), "tonezen-documents-"));

    const logPath = await appendDiagnosticError(documentsPath, {
      area: "download",
      message: "Не удалось скачать",
      code: "__download_transfer_failed__",
      bookId: "book-1",
      trackId: "track-10",
      bookTitle: "Книга",
      trackTitle: "010",
    });

    expect(logPath).toBe(path.join(documentsPath, "Tonezen", "tonezen-errors.log"));
    const lines = fs.readFileSync(logPath, "utf-8").trim().split("\n");
    expect(lines).toHaveLength(1);
    expect(JSON.parse(lines[0])).toMatchObject({
      area: "download",
      message: "Не удалось скачать",
      code: "__download_transfer_failed__",
      bookId: "book-1",
      trackId: "track-10",
      bookTitle: "Книга",
      trackTitle: "010",
    });
  });
});
