import fs from "node:fs/promises";
import path from "node:path";

export interface DiagnosticErrorEntry {
  area: "download" | "playback" | "sync" | "app";
  message: string;
  code?: string;
  bookId?: string;
  trackId?: string;
  bookTitle?: string;
  trackTitle?: string;
  details?: string;
}

export async function appendDiagnosticError(
  documentsPath: string,
  entry: DiagnosticErrorEntry,
): Promise<string> {
  const logDir = path.join(documentsPath, "Tonezen");
  const logPath = path.join(logDir, "tonezen-errors.log");
  await fs.mkdir(logDir, { recursive: true });
  const line = JSON.stringify({
    timestamp: new Date().toISOString(),
    ...entry,
  });
  await fs.appendFile(logPath, `${line}\n`, "utf-8");
  return logPath;
}
