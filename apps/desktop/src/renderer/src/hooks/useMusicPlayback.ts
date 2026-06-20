import { useCallback, useMemo, useRef, useState, type Dispatch, type SetStateAction } from "react";
import type { Book, SessionState, Track } from "@shared/types";
import {
  buildMusicTrackListForCatalogUpdate,
  musicQueueFrom,
  musicQueueWindowFrom,
  nextMusicIndex,
  previousMusicIndex,
  visibleMusicTrackList,
  type MusicListTrack,
} from "@shared/musicList";
import {
  isBulkDownloading,
  isTrackQueued,
  progressForTrack as downloadProgressForTrack,
} from "@shared/downloadQueueState";
import {
  findFirstPlayableMusicTrack,
  findNextPlayableIndex,
  findPreviousPlayableIndex,
  isMusicTrackPlayable,
  shouldRestartCurrentMusicTrack,
  type MusicSessionState,
} from "@shared/musicPlayback";
import type { useDownloadQueue } from "./useDownloadQueue";
import { strings } from "../i18n/strings";

interface UseMusicPlaybackOptions {
  books: Book[];
  allTracks: Track[];
  musicTracks: MusicListTrack[];
  setMusicTracks: Dispatch<SetStateAction<MusicListTrack[]>>;
  setTracks: Dispatch<SetStateAction<Track[]>>;
  sessionState: SessionState;
  refreshLibrary: () => Promise<void>;
  downloadQueue: ReturnType<typeof useDownloadQueue>;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  seekTo: (fraction: number) => void;
  currentTrack: Track | null;
  positionMs: number;
}

function musicSessionState(sessionState: SessionState): MusicSessionState {
  if (sessionState === "AuthenticatedOnline") return "AuthenticatedOnline";
  if (sessionState === "Unauthenticated") return "Unauthenticated";
  return "AuthenticatedOffline";
}

export function useMusicPlayback({
  books,
  allTracks,
  musicTracks,
  setMusicTracks,
  setTracks,
  sessionState,
  refreshLibrary,
  downloadQueue,
  playTrack,
  stopPlayback,
  pauseOrResume,
  seekTo,
  currentTrack,
  positionMs,
}: UseMusicPlaybackOptions) {
  const [musicMode, setMusicMode] = useState(false);
  const [musicPlaybackBook, setMusicPlaybackBook] = useState<Book | null>(null);
  const [musicError, setMusicError] = useState<string | null>(null);
  const musicQueueRef = useRef<MusicListTrack[]>([]);
  const deletingTrackIdRef = useRef<string | null>(null);
  const [musicQueue, setMusicQueue] = useState<MusicListTrack[]>([]);
  const musicStartedInSessionRef = useRef(false);
  const prefetchJobRef = useRef(0);

  const playbackMusicTracks = useMemo(
    () => visibleMusicTrackList(musicTracks, sessionState === "AuthenticatedOnline"),
    [musicTracks, sessionState],
  );

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
          setMusicError(strings.musicPlaybackErrorLogin);
        }
        return null;
      }
      if (sessionState !== "AuthenticatedOnline") {
        if (!options?.suppressPlaybackError) {
          setMusicError(strings.musicPlaybackErrorOffline);
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
          setMusicError(strings.musicPlaybackErrorDownload);
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
    const firstPlayable = findFirstPlayableMusicTrack(
      playbackMusicTracks,
      musicSessionState(sessionState),
    );
    if (!firstPlayable) {
      if (sessionState === "Unauthenticated") {
        setMusicError(strings.musicPlaybackErrorLogin);
      } else if (sessionState !== "AuthenticatedOnline") {
        setMusicError(strings.musicPlaybackErrorOffline);
      }
      return;
    }
    void playMusicTrackInternal(firstPlayable);
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

  const downloadAllMusic = useCallback(async () => {
    if (sessionState !== "AuthenticatedOnline") return;
    if (isBulkDownloading(downloadQueue.state)) {
      if (downloadQueue.state.activeBatchId) {
        await downloadQueue.cancelBatch(downloadQueue.state.activeBatchId);
      }
      return;
    }
    const pending = musicTracks.filter((track) => !track.isDownloaded);
    if (pending.length === 0) return;
    const batchId = crypto.randomUUID();
    await downloadQueue.enqueueBatch(
      pending.map((track) => ({
        bookId: track.bookId,
        trackId: track.trackId,
        priority: "BULK" as const,
        batchId,
        title: track.trackTitle,
        subtitle: track.artist,
        contentType: "music",
      })),
      batchId,
    );
  }, [downloadQueue, musicTracks, sessionState]);

  const downloadMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {
      if (sessionState !== "AuthenticatedOnline" || listTrack.isDownloaded) return;
      setMusicError(null);
      const queued =
        isTrackQueued(downloadQueue.state, listTrack.trackId) ||
        downloadProgressForTrack(downloadQueue.state, listTrack.trackId) != null;
      if (queued) {
        await downloadQueue.cancelTrack(listTrack.bookId, listTrack.trackId);
        return;
      }
      await downloadQueue.enqueue({
        bookId: listTrack.bookId,
        trackId: listTrack.trackId,
        priority: "USER",
        title: listTrack.trackTitle,
        subtitle: listTrack.artist,
        contentType: "music",
      });
    },
    [downloadQueue, sessionState],
  );

  const deleteMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {

      const trackId = listTrack.trackId;
      deletingTrackIdRef.current = trackId;
      prefetchJobRef.current += 1;

      try {
        await downloadQueue.cancelTrack(listTrack.bookId, listTrack.trackId);
        if (currentTrack?.id === trackId) {
          stopPlayback();
          setMusicMode(false);
          setMusicPlaybackBook(null);
          musicQueueRef.current = [];
          setMusicQueue([]);
          await new Promise((resolve) => setTimeout(resolve, 50));
        }

        await window.tonezen.download.delete(listTrack.bookId, trackId);
        await refreshLibrary();

        setMusicQueue((queue) =>
          queue.map((item) => (item.trackId === trackId ? { ...item, isDownloaded: false } : item)),
        );
        musicQueueRef.current = musicQueueRef.current.map((item) =>
          item.trackId === trackId ? { ...item, isDownloaded: false } : item,
        );
      } finally {
        deletingTrackIdRef.current = null;
      }
    },
    [currentTrack?.id, downloadQueue, refreshLibrary, stopPlayback],
  );

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

  const resetMusicSession = useCallback(() => {
    setMusicMode(false);
    setMusicPlaybackBook(null);
    musicStartedInSessionRef.current = false;
    musicQueueRef.current = [];
    setMusicQueue([]);
    prefetchJobRef.current += 1;
    setMusicError(null);
  }, []);

  return {
    musicMode,
    setMusicMode,
    musicPlaybackBook,
    musicError,
    musicQueue,
    musicStartedInSessionRef,
    musicQueueRef,
    playMusicTrack: playMusicTrackInternal,
    playMusicWave,
    downloadAllMusic,
    downloadMusicTrack,
    deleteMusicTrack,
    onMusicTabSelected,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
    onMiniPlayerPlayPause,
    resetMusicSession,
  };
}
