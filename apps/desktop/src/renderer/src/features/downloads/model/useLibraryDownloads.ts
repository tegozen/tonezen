import { useCallback, useEffect } from "react";
import type { Book, Cycle, SessionState, Track } from "@core/types";
import {
  getTonezenApi,
  useDeleteAllDownloadsMutation,
  useDeleteDownloadMutation,
} from "@/shared/api";
import type { useDownloadQueue } from "./useDownloadQueue";
import type { RefreshLibraryOptions } from "@/features/library";

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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

interface UseLibraryDownloadsOptions {
  sessionState: SessionState;
  downloadQueue: ReturnType<typeof useDownloadQueue>;
  currentTrack: Track | null;
  selectedBook: Book | null;
  setTracks: (tracks: Track[]) => void;
  stopPlayback: () => void;
  closeExpandedPlayer: () => void;
  refreshLibrary: (options?: RefreshLibraryOptions) => Promise<void>;
  showToast: (message: string) => void;
}

export function useLibraryDownloads({
  sessionState,
  downloadQueue,
  currentTrack,
  selectedBook,
  setTracks,
  stopPlayback,
  closeExpandedPlayer,
  refreshLibrary,
  showToast,
}: UseLibraryDownloadsOptions) {
  const api = getTonezenApi();
  const deleteDownload = useDeleteDownloadMutation();
  const deleteAllDownloadsMutation = useDeleteAllDownloadsMutation();

  useEffect(() => {
    return api.download.onFailed(() => {
      showToast("Не удалось скачать");
    });
  }, [api, showToast]);

  const downloadAllBookTracks = useCallback(
    async (book: Book) => {
      const bookTracks = await api.db.getTracks(book.id);
      const missing = bookTracks.filter((track) => !track.localPath);
      if (missing.length === 0) return;
      const batchId = crypto.randomUUID();
      try {
        await downloadQueue.enqueueBatch(
          missing.map((track) => ({
            bookId: book.id,
            trackId: track.id,
            priority: "USER" as const,
            batchId,
            title: track.title,
            subtitle: book.title,
            contentType: book.contentType,
          })),
          batchId,
        );
      } catch (e) {
        const message = e instanceof Error ? e.message : "";
        if (sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated") {
          showToast("Нет сети");
        } else {
          showToast(
            message === "__download_auth_required__"
              ? "Войдите в аккаунт, чтобы скачать трек"
              : "Не удалось скачать",
          );
        }
      }
    },
    [api, downloadQueue, sessionState, showToast],
  );

  const downloadBookTrack = useCallback(
    async (book: Book, track: Track) => {
      if (track.localPath) return;
      if (sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated") {
        showToast("Нет сети");
        return;
      }
      try {
        await downloadQueue.enqueue({
          bookId: book.id,
          trackId: track.id,
          priority: "USER",
          title: track.title,
          subtitle: book.title,
          contentType: book.contentType,
        });
      } catch (e) {
        const message = e instanceof Error ? e.message : "";
        let errorText: string;
        switch (message) {
          case "__download_auth_required__":
            errorText = "Войдите в аккаунт, чтобы скачать трек";
            break;
          case "__download_sign_failed__":
          case "__download_no_signed_url__":
          case "__download_transfer_failed__":
            errorText = "Не удалось скачать трек";
            break;
          default:
            errorText = "Не удалось скачать";
        }
        showToast(errorText);
      }
    },
    [downloadQueue, sessionState, showToast],
  );

  const downloadCycle = useCallback(
    async (cycle: Cycle) => {
      const batchId = crypto.randomUUID();
      const requests = [];
      for (const book of cycle.books) {
        const bookTracks = await api.db.getTracks(book.id);
        for (const track of bookTracks) {
          if (!track.localPath) {
            requests.push({
              bookId: book.id,
              trackId: track.id,
              priority: "BULK" as const,
              batchId,
              title: track.title,
              subtitle: book.title,
              contentType: book.contentType,
            });
          }
        }
      }
      if (requests.length > 0) {
        await downloadQueue.enqueueBatch(requests, batchId);
      }
    },
    [api, downloadQueue],
  );

  const removeBookDownloads = useCallback(
    async (book: Book) => {
      const bookTracks = await api.db.getTracks(book.id);
      if (currentTrack && bookTracks.some((t) => t.id === currentTrack.id)) {
        stopPlayback();
        await delay(50);
      }
      for (const track of bookTracks) {
        if (track.localPath) {
          await deleteDownload.mutateAsync({ bookId: book.id, trackId: track.id });
        }
      }
      await refreshLibrary();
      if (selectedBook?.id === book.id) {
        setTracks(await api.db.getTracks(book.id));
      }
    },
    [api, currentTrack, deleteDownload, refreshLibrary, selectedBook, setTracks, stopPlayback],
  );

  const removeTrackDownload = useCallback(
    async (book: Book, track: Track) => {
      if (!track.localPath) return;
      if (currentTrack?.id === track.id) {
        stopPlayback();
        await delay(50);
      }
      await deleteDownload.mutateAsync({ bookId: book.id, trackId: track.id });
      await refreshLibrary();
      if (selectedBook?.id === book.id) {
        setTracks(await api.db.getTracks(book.id));
      }
    },
    [api, currentTrack, deleteDownload, refreshLibrary, selectedBook, setTracks, stopPlayback],
  );

  const deleteAllDownloads = useCallback(async () => {
    stopPlayback();
    closeExpandedPlayer();
    await delay(50);
    await downloadQueue.cancelAll();
    await deleteAllDownloadsMutation.mutateAsync();
    await refreshLibrary();
  }, [
    closeExpandedPlayer,
    deleteAllDownloadsMutation,
    downloadQueue,
    refreshLibrary,
    stopPlayback,
  ]);

  return {
    downloadAllBookTracks,
    downloadBookTrack,
    downloadCycle,
    removeBookDownloads,
    removeTrackDownload,
    deleteAllDownloads,
  };
}
