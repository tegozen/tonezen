export interface ProgressRecord {
  book_id: string;
  track_id: string;
  position_ms: number;
  updated_at: string;
  revision: number;
}

export type ProgressConflictResult = {
  conflict: true;
  progress: ProgressRecord;
};

/** Returns conflict payload when base_revision does not match the existing server row. */
export function maybeProgressCasConflict(
  baseRevision: number,
  existing: ProgressRecord | null | undefined,
): ProgressConflictResult | null {
  if (!existing) {
    if (baseRevision !== 0) {
      // No row but client thinks there is one — treat as conflict with empty impossible;
      // caller should still attempt insert only when baseRevision === 0.
      return null;
    }
    return null;
  }
  if (baseRevision === existing.revision) return null;
  return { conflict: true, progress: existing };
}

export function parseBaseRevision(value: unknown): number | null {
  if (typeof value === "number" && Number.isInteger(value) && value >= 0) {
    return value;
  }
  if (typeof value === "string" && value.trim() !== "") {
    const n = Number(value);
    if (Number.isInteger(n) && n >= 0) return n;
  }
  return null;
}
