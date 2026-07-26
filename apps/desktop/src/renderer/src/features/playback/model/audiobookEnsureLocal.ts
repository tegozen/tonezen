import { useCallback } from "react";
import type { Book, Track } from "@core/types";
import { nextChapterInBook } from "@core/downloads/audiobookDownloadTarget";
import { getTonezenApi } from "@/shared/api";
import { logDownloadFailure } from "@/shared/lib/diagnostics";
import type { UseAudiobookSessionOptions } from "./audiobookSessionTypes";

type UseAudiobookEnsureLocalOptions = Pick<
  UseAudiobookSessionOptions,
  | "sessionState"
  | "books"
  | "downloadQueue"
  | "refreshLibrary"
  | "playTrack"
  | "stopPlayback"
  | "showToast"
  | "closeExpandedPlayer"
  | "music"
>;

export function useAudiobookEnsureLocal({
  sessionState,
  books,
  downloadQueue,
  refreshLibrary,
  playTrack,
  stopPlayback,
  showToast,
  closeExpandedPlayer,
  music,
}: UseAudiobookEnsureLocalOptions) {
  const api = getTonezenApi();

  const ensureAudiobookTrackLocal = useCallback(
    async (bookId: string, trackId: string): Promise<Track | null> => {
      let bookTracks = await api.db.getTracks(bookId);
      let track = bookTracks.find((item) => item.id === trackId);
      if (track?.localPath) return track;
      const book = books.find((item) => item.id === bookId);
      const trackMeta = bookTracks.find((item) => item.id === trackId);
      if (sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated") {
        logDownloadFailure({
          code: sessionState === "AuthenticatedOffline" ? "OFFLINE" : "UNAUTHENTICATED",
          bookId,
          trackId,
          bookTitle: book?.title,
          trackTitle: trackMeta?.title,
        });
        return null;
      }
      try {
        const result = await downloadQueue.awaitTrack(bookId, trackId, {
          priority: "PLAY",
          title: trackMeta?.title ?? trackId,
          subtitle: book?.title ?? null,
          contentType: book?.contentType ?? "audiobook",
        });
        if (result !== "COMPLETED") {
          if (result !== "FAILED") {
            logDownloadFailure({
              code: result,
              bookId,
              trackId,
              bookTitle: book?.title,
              trackTitle: trackMeta?.title,
            });
          }
          return null;
        }
        bookTracks = await api.db.getTracks(bookId);
        track = bookTracks.find((item) => item.id === trackId);
        await refreshLibrary();
        return track ?? null;
      } catch (e) {
        logDownloadFailure({
          code: e instanceof Error ? e.message : "UNKNOWN",
          bookId,
          trackId,
          bookTitle: book?.title,
          trackTitle: trackMeta?.title,
        });
        return null;
      }
    },
    [api, books, downloadQueue, refreshLibrary, sessionState],
  );

  const prefetchNextAudiobookChapter = useCallback(
    async (book: Book, bookTracks: Track[], currentTrack: Track) => {
      if (sessionState !== "AuthenticatedOnline") return;
      const next = nextChapterInBook(bookTracks, currentTrack.id);
      if (!next || next.localPath) return;
      try {
        await downloadQueue.enqueue({
          bookId: book.id,
          trackId: next.id,
          priority: "PREFETCH",
          title: next.title,
          subtitle: book.title,
          contentType: book.contentType,
        });
      } catch {
        // Prefetch is best-effort.
      }
    },
    [downloadQueue, sessionState],
  );

  const playAudiobookTrackResolved = useCallback(
    async (book: Book, bookTracks: Track[], track: Track, startMs: number): Promise<boolean> => {
      music.setMusicMode(false);
      const local = track.localPath ? track : await ensureAudiobookTrackLocal(book.id, track.id);
      if (local?.localPath) {
        playTrack(local, startMs, book);
        closeExpandedPlayer();
        void prefetchNextAudiobookChapter(book, bookTracks, local);
        return true;
      }
      if (!track.localPath) {
        const offlineMessage =
          sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated"
            ? "Нет сети — нужен интернет для первой загрузки"
            : "Не удалось скачать";
        showToast(offlineMessage);
        stopPlayback();
      }
      return false;
    },
    [
      closeExpandedPlayer,
      ensureAudiobookTrackLocal,
      music,
      playTrack,
      prefetchNextAudiobookChapter,
      sessionState,
      showToast,
      stopPlayback,
    ],
  );

  const advanceAudiobookTrack = useCallback(
    async (book: Book, bookTracks: Track[], nextTrack: Track) => {
      if (!nextTrack.localPath && sessionState !== "AuthenticatedOnline") {
        showToast("Нет сети — нужен интернет для первой загрузки");
        stopPlayback();
        return;
      }
      const local = nextTrack.localPath ? nextTrack : await ensureAudiobookTrackLocal(book.id, nextTrack.id);
      if (!local?.localPath) {
        showToast("Не удалось скачать");
        stopPlayback();
        return;
      }
      playTrack(local, 0, book);
      void prefetchNextAudiobookChapter(book, bookTracks, local);
    },
    [ensureAudiobookTrackLocal, playTrack, prefetchNextAudiobookChapter, sessionState, showToast, stopPlayback],
  );

  return {
    ensureAudiobookTrackLocal,
    playAudiobookTrackResolved,
    advanceAudiobookTrack,
  };
}
