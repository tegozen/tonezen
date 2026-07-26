import fs from "node:fs/promises";
import path from "node:path";
import * as Sentry from "@sentry/electron/main";

const ALLOWED_AREAS = new Set(["download", "playback", "sync", "app"] as const);
const MAX_FIELD_LENGTH = 500;
const MAX_LOG_BYTES = 2 * 1024 * 1024;

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

function truncate(value: unknown, max = MAX_FIELD_LENGTH): string | undefined {
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  return trimmed.length > max ? `${trimmed.slice(0, max)}…` : trimmed;
}

export function sanitizeDiagnosticEntry(raw: unknown): DiagnosticErrorEntry | null {
  if (!raw || typeof raw !== "object") return null;
  const entry = raw as Record<string, unknown>;
  const area = typeof entry.area === "string" ? entry.area : "";
  if (!ALLOWED_AREAS.has(area as DiagnosticErrorEntry["area"])) return null;
  const message = truncate(entry.message);
  if (!message) return null;
  return {
    area: area as DiagnosticErrorEntry["area"],
    message,
    code: truncate(entry.code, 64),
    bookId: truncate(entry.bookId, 64),
    trackId: truncate(entry.trackId, 64),
    bookTitle: truncate(entry.bookTitle),
    trackTitle: truncate(entry.trackTitle),
    details: truncate(entry.details),
  };
}

async function rotateLogIfNeeded(logPath: string): Promise<void> {
  try {
    const stat = await fs.stat(logPath);
    if (stat.size < MAX_LOG_BYTES) return;
    await fs.rename(logPath, `${logPath}.1`);
  } catch {
    // Missing file is fine.
  }
}

export async function appendDiagnosticError(
  documentsPath: string,
  entry: DiagnosticErrorEntry | unknown,
): Promise<string | null> {
  const sanitized = sanitizeDiagnosticEntry(entry);
  if (!sanitized) return null;
  try {
    Sentry.captureMessage(`[${sanitized.area}] ${sanitized.message}`, "error");
  } catch {
    // Sentry may be uninitialized when DSN is empty.
  }
  const logDir = path.join(documentsPath, "Tonezen");
  const logPath = path.join(logDir, "tonezen-errors.log");
  await fs.mkdir(logDir, { recursive: true });
  await rotateLogIfNeeded(logPath);
  const line = JSON.stringify({
    timestamp: new Date().toISOString(),
    ...sanitized,
  });
  await fs.appendFile(logPath, `${line}\n`, "utf-8");
  return logPath;
}
