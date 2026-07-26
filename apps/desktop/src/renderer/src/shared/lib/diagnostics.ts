import { getTonezenApi } from "@/shared/api/tonezen";

export function logDownloadFailure(input: {
  code: string;
  bookId: string;
  trackId: string;
  bookTitle?: string;
  trackTitle?: string;
  details?: string;
}) {
  void getTonezenApi()
    .diagnostics.logError({
      area: "download",
      message: "Не удалось скачать",
      ...input,
    })
    .catch(() => {});
}
