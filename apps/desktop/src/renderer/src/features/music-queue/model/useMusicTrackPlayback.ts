import { useCallback } from "react";
import {
  buildMusicTrackListForCatalogUpdate,
  musicQueueFrom,
  musicQueueWindowFrom,
  type MusicListTrack,
} from "@core/catalog/musicList";
import { progressForTrack as downloadProgressForTrack } from "@core/downloads/downloadQueueState";
import { resolveMusicWaveDisplayTrack } from "@core/playback/musicPlayback";
import type { MusicPlayControlsDeps } from "./musicPlaybackDeps";

export type PlayMusicTrackFn = (
  listTrack: MusicListTrack,
  options?: {
    showDownloadProgress?: boolean;
    allowWhileDownloading?: boolean;
    advancePlayback?: boolean;
  },
) => Promise<boolean>;

type MusicTrackPlaybackDeps = Pick<
  MusicPlayControlsDeps,
  | "books"
  | "allTracks"
  | "musicTracks"
  | "setMusicTracks"
  | "setTracks"
  | "sessionState"
  | "downloadQueue"
  | "playTrack"
  | "pauseOrResume"
  | "currentTrack"
  | "musicMode"
  | "setMusicMode"
  | "setMusicPlaybackBook"
  | "setMusicError"
  | "musicQueueRef"
  | "deletingTrackIdRef"
  | "setMusicQueue"
  | "musicStartedInSessionRef"
  | "playbackMusicTracks"
  | "ensureTrackLocal"
  | "resolveLocalTrack"
  | "prefetchNextTrack"
>;

export function useMusicTrackPlayback({
  books,
  allTracks,
  setMusicTracks,
  setTracks,
  sessionState,
  downloadQueue,
  playTrack,
  pauseOrResume,
  currentTrack,
  musicMode,
  setMusicMode,
  setMusicPlaybackBook,
  setMusicError,
  musicQueueRef,
  deletingTrackIdRef,
  setMusicQueue,
  musicStartedInSessionRef,
  playbackMusicTracks,
  ensureTrackLocal,
  resolveLocalTrack,
  prefetchNextTrack,
}: MusicTrackPlaybackDeps) {
  const playMusicTrack: PlayMusicTrackFn = useCallback(
    async (listTrack, options) => {
      if (deletingTrackIdRef.current === listTrack.trackId) return false;

      const book = books.find((item) => item.id === listTrack.bookId);
      if (!book) return false;

      if (
        musicMode &&
        currentTrack?.id === listTrack.trackId &&
        !downloadProgressForTrack(downloadQueue.state, listTrack.trackId)
      ) {
        if (!listTrack.isDownloaded) {
          const local = await ensureTrackLocal(listTrack.bookId, listTrack.trackId);
          if (!local?.localPath) return false;
          setMusicPlaybackBook(book);
          playTrack(local, 0, book);
          return true;
        }
        pauseOrResume();
        return true;
      }

      setMusicError(null);
      setMusicMode(true);
      setMusicPlaybackBook(book);
      musicStartedInSessionRef.current = true;

      const showDownloadProgress = options?.showDownloadProgress ?? true;
      let local = listTrack.isDownloaded ? await resolveLocalTrack(listTrack) : null;

      if (!local?.localPath) {
        local = await ensureTrackLocal(listTrack.bookId, listTrack.trackId, {
          title: listTrack.trackTitle,
          subtitle: listTrack.artist,
          priority: showDownloadProgress ? "PLAY" : "PREFETCH",
          suppressPlaybackError: options?.advancePlayback,
        });
      }

      if (!local?.localPath) return false;

      const queue = musicQueueFrom(playbackMusicTracks, listTrack.trackId);
      musicQueueRef.current = queue;
      setMusicQueue(musicQueueWindowFrom(playbackMusicTracks, listTrack.trackId));
      setTracks([local]);
      playTrack(local, 0, book);
      prefetchNextTrack(queue, listTrack.trackId);
      return true;
    },
    [
      books,
      currentTrack?.id,
      deletingTrackIdRef,
      downloadQueue,
      ensureTrackLocal,
      musicMode,
      musicQueueRef,
      musicStartedInSessionRef,
      pauseOrResume,
      playbackMusicTracks,
      playTrack,
      prefetchNextTrack,
      resolveLocalTrack,
      setMusicError,
      setMusicMode,
      setMusicPlaybackBook,
      setMusicQueue,
      setTracks,
    ],
  );

  const onMusicTabSelected = useCallback(() => {
    if (musicStartedInSessionRef.current) return;
    setMusicTracks((current) =>
      buildMusicTrackListForCatalogUpdate(current, books, allTracks, false),
    );
  }, [allTracks, books, musicStartedInSessionRef, setMusicTracks]);

  const playMusicWave = useCallback(() => {
    if (musicMode && currentTrack) {
      pauseOrResume();
      return;
    }
    const displayTrack = resolveMusicWaveDisplayTrack(
      playbackMusicTracks,
      currentTrack?.id ?? null,
      musicMode,
    );
    if (!displayTrack) {
      if (sessionState === "Unauthenticated") {
        setMusicError("Войдите в аккаунт, чтобы скачать трек");
      } else if (sessionState !== "AuthenticatedOnline") {
        setMusicError("Нет сети — нужен интернет для первой загрузки");
      }
      return;
    }
    void playMusicTrack(displayTrack, {
      showDownloadProgress: !displayTrack.isDownloaded,
      advancePlayback: true,
    });
  }, [
    currentTrack,
    musicMode,
    pauseOrResume,
    playMusicTrack,
    playbackMusicTracks,
    sessionState,
    setMusicError,
  ]);

  return { playMusicTrack, playMusicWave, onMusicTabSelected };
}
