export function parseUpdatedSince(query: Record<string, unknown>): string | undefined {
  const value = query.updated_since;
  return typeof value === "string" ? value : undefined;
}
