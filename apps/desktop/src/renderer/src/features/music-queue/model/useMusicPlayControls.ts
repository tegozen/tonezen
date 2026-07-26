import { useCallback } from "react";
import {
  buildMusicTrackListForCatalogUpdate,
  musicQueueFrom,
  musicQueueWindowFrom,
  nextMusicIndex,
  previousMusicIndex,
  type MusicListTrack,
} from "@core/catalog/musicList";
import { progressForTrack as downloadProgressForTrack } from "@core/downloads/downloadQueueState";
import {
  findNextPlayableIndex,
  findPreviousPlayableIndex,
  resolveMusicWaveDisplayTrack,
  shouldRestartCurrentMusicTrack,
} from "@core/playback/musicPlayback";
import type { MusicPlayControlsDeps } from "./musicPlaybackDeps";

export function useMusicPlayControls({
  books,
  allTracks,
  musicTracks,
  setMusicTracks,
  setTracks,
  sessionState,
  downloadQueue,
  playTrack,
  stopPlayback,
  pauseOrResume,
  seekTo,
  currentTrack,
  positionMs,
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
  isTrackPlayable,
}: MusicPlayControlsDeps) {
  const playMusicTrackInternal = useCallback(
    async (
      listTrack: MusicListTrack,
      options?: {
        showDownloadProgress?: boolean;
        allowWhileDownloading?: boolean;
        advancePlayback?: boolean;
      },
    ) => {
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
      ensureTrackLocal,
      downloadQueue,
      musicMode,
      musicTracks,
      pauseOrResume,
      playTrack,
      playbackMusicTracks,
      prefetchNextTrack,
      resolveLocalTrack,
      setTracks,
    ],
  );

  const onMusicTabSelected = useCallback(() => {
    if (musicStartedInSessionRef.current) return;
    setMusicTracks((current) =>
      buildMusicTrackListForCatalogUpdate(current, books, allTracks, false),
    );
  }, [allTracks, books, setMusicTracks]);

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
    void playMusicTrackInternal(displayTrack, {
      showDownloadProgress: !displayTrack.isDownloaded,
      advancePlayback: true,
    });
  }, [
    currentTrack,
    musicMode,
    pauseOrResume,
    playMusicTrackInternal,
    playbackMusicTracks,
    sessionState,
  ]);

  const advanceToPlayableTrack = useCallback(
    async (
      queue: MusicListTrack[],
      fromIndex: number,
      direction: "next" | "previous",
    ): Promise<boolean> => {
      const stepper = direction === "next" ? nextMusicIndex : previousMusicIndex;
      const finder = direction === "next" ? findNextPlayableIndex : findPreviousPlayableIndex;
      let index = fromIndex;
      for (let step = 0; step < queue.length - 1; step += 1) {
        const nextIndex = finder(queue, index, isTrackPlayable, stepper);
        if (nextIndex == null) return false;
        const played = await playMusicTrackInternal(queue[nextIndex], {
          showDownloadProgress: false,
          advancePlayback: true,
        });
        if (played) return true;
        index = nextIndex;
      }
      return false;
    },
    [isTrackPlayable, playMusicTrackInternal],
  );

  const handleSkipNext = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void advanceToPlayableTrack(queue, index, "next");
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode]);

  const handleSkipPrevious = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    if (shouldRestartCurrentMusicTrack(positionMs)) {
      seekTo(0);
      return true;
    }
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void advanceToPlayableTrack(queue, index, "previous");
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode, positionMs, seekTo]);

  const handleTrackEnded = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void (async () => {
      const advanced = await advanceToPlayableTrack(queue, index, "next");
      if (!advanced) {
        stopPlayback();
      }
    })();
    return true;
  }, [advanceToPlayableTrack, currentTrack, musicMode, stopPlayback]);

  const onMiniPlayerPlayPause = useCallback(
    (listTrack: MusicListTrack | undefined) => {
      if (musicMode && currentTrack && listTrack?.trackId === currentTrack.id) {
        pauseOrResume();
        return;
      }
      if (musicMode && listTrack) {
        void playMusicTrackInternal(listTrack);
        return;
      }
      pauseOrResume();
    },
    [currentTrack, musicMode, pauseOrResume, playMusicTrackInternal],
  );

  return {
    playMusicTrack: playMusicTrackInternal,
    playMusicWave,
    onMusicTabSelected,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
  };
}
