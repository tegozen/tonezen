/** Returns undefined when omitted, false when present but invalid. */
export function parseUpdatedSince(
  query: Record<string, unknown>,
): string | undefined | false {
  if (!("updated_since" in query)) return undefined;
  const value = query.updated_since;
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) return false;
  return value;
}
