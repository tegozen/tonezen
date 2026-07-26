import { useCallback } from "react";
import type { Track } from "@core/types";
import { nextMusicIndex, type MusicListTrack } from "@core/catalog/musicList";
import { isMusicTrackPlayable, type MusicSessionState } from "@core/playback/musicPlayback";
import type { MusicEnsureLocalDeps } from "./musicPlaybackDeps";

function musicSessionState(sessionState: MusicEnsureLocalDeps["sessionState"]): MusicSessionState {
  if (sessionState === "AuthenticatedOnline") return "AuthenticatedOnline";
  if (sessionState === "Unauthenticated") return "Unauthenticated";
  return "AuthenticatedOffline";
}

export function useMusicEnsureLocal({
  sessionState,
  musicTracks,
  allTracks,
  downloadQueue,
  refreshLibrary,
  setMusicError,
}: MusicEnsureLocalDeps) {
  const isTrackPlayable = useCallback(
    (track: MusicListTrack) => isMusicTrackPlayable(track, musicSessionState(sessionState)),
    [sessionState],
  );

  const ensureTrackLocal = useCallback(
    async (
      bookId: string,
      trackId: string,
      options?: {
        title?: string;
        subtitle?: string | null;
        priority?: "PLAY" | "USER" | "BULK" | "PREFETCH";
        suppressPlaybackError?: boolean;
      },
    ): Promise<Track | null> => {
      let bookTracks = await window.tonezen.db.getTracks(bookId);
      let track = bookTracks.find((item) => item.id === trackId);
      if (track?.localPath) return track as Track;

      if (sessionState === "Unauthenticated") {
        if (!options?.suppressPlaybackError) {
          setMusicError("Войдите в аккаунт, чтобы скачать трек");
        }
        return null;
      }
      if (sessionState !== "AuthenticatedOnline") {
        if (!options?.suppressPlaybackError) {
          setMusicError("Нет сети — нужен интернет для первой загрузки");
        }
        return null;
      }

      const listTrack = musicTracks.find((item) => item.trackId === trackId);
      const result = await downloadQueue.awaitTrack(bookId, trackId, {
        priority: options?.priority ?? "PLAY",
        title: options?.title ?? listTrack?.trackTitle ?? track?.title ?? trackId,
        subtitle: options?.subtitle ?? listTrack?.artist ?? null,
        contentType: "music",
      });

      if (result !== "COMPLETED") {
        if (result === "FAILED" && !options?.suppressPlaybackError) {
          setMusicError("Не удалось скачать трек");
        }
        return null;
      }

      bookTracks = await window.tonezen.db.getTracks(bookId);
      track = bookTracks.find((item) => item.id === trackId);
      await refreshLibrary();
      return (track as Track) ?? null;
    },
    [downloadQueue, musicTracks, refreshLibrary, sessionState],
  );

  const resolveLocalTrack = useCallback(
    async (listTrack: MusicListTrack): Promise<Track | null> => {
      const cached = allTracks.find((item) => item.id === listTrack.trackId);
      if (cached?.localPath) return cached;
      const bookTracks = await window.tonezen.db.getTracks(listTrack.bookId);
      const track = bookTracks.find((item) => item.id === listTrack.trackId);
      return track?.localPath ? (track as Track) : null;
    },
    [allTracks],
  );

  const prefetchNextTrack = useCallback(
    (queue: MusicListTrack[], currentTrackId: string) => {
      const index = queue.findIndex((item) => item.trackId === currentTrackId);
      if (index < 0 || queue.length <= 1) return;
      const next = queue[nextMusicIndex(index, queue.length)];
      if (!next || next.isDownloaded) return;
      void downloadQueue.enqueue({
        bookId: next.bookId,
        trackId: next.trackId,
        priority: "PREFETCH",
        title: next.trackTitle,
        subtitle: next.artist,
        contentType: "music",
      });
    },
    [downloadQueue],
  );

  return {
    isTrackPlayable,
    ensureTrackLocal,
    resolveLocalTrack,
    prefetchNextTrack,
  };
}
