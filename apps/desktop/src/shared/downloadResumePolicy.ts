export const COMPLETED_HISTORY_LIMIT = 200;

export type ResumeAction = "FULL_DOWNLOAD" | "RANGE_APPEND" | "RESTART";

export function resolveResumeAction(
  partFileLength: number,
  bytesDownloaded: number,
  totalBytes: number | null,
  rangeResponseCode: number | null,
): ResumeAction {
  if (partFileLength <= 0 && bytesDownloaded <= 0) return "FULL_DOWNLOAD";
  if (totalBytes != null && totalBytes > 0 && partFileLength > totalBytes) return "RESTART";
  if (partFileLength !== bytesDownloaded && bytesDownloaded > 0) {
    return partFileLength > bytesDownloaded ? "RESTART" : "RANGE_APPEND";
  }
  switch (rangeResponseCode) {
    case 206:
      return "RANGE_APPEND";
    case 200:
    case 416:
      return "RESTART";
    case null:
      return partFileLength > 0 ? "RANGE_APPEND" : "FULL_DOWNLOAD";
    default:
      return "RESTART";
  }
}

export function progressFraction(bytesDownloaded: number, totalBytes: number | null): number | null {
  if (totalBytes == null || totalBytes <= 0) return null;
  return Math.min(Math.max(bytesDownloaded / totalBytes, 0), 1);
}
