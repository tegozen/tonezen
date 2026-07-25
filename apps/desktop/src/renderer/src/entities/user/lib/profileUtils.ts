export function formatMemberSinceDate(epochMs: number | null | undefined): string | null {
  if (epochMs == null) return null;
  const date = new Date(epochMs);
  if (Number.isNaN(date.getTime())) return null;
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = date.getFullYear();
  return `${day}.${month}.${year}`;
}

/** Local time as H:mm — matches Android ProfileViewModel syncTimeFormatter. */
export function formatSyncTime(epochMs: number | null | undefined): string | null {
  if (epochMs == null) return null;
  const date = new Date(epochMs);
  if (Number.isNaN(date.getTime())) return null;
  return `${date.getHours()}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function formatLastSyncLabel(
  epochMs: number | null | undefined,
  labels: { todayAt: string; never: string },
): string {
  const time = formatSyncTime(epochMs);
  if (time) return labels.todayAt.replace("{time}", time);
  return labels.never;
}
