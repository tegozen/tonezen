import { naturalCompare } from "../parsers.js";

export function mergePartialBookOrder(existingOrder: unknown, partialOrder: string[]): string[] {
  const merged = Array.isArray(existingOrder)
    ? existingOrder.filter((value): value is string => typeof value === "string")
    : [];
  for (const slug of partialOrder) {
    if (!merged.includes(slug)) {
      merged.push(slug);
    }
  }
  return merged.sort(naturalCompare);
}
