import { useCallback, useRef, useState, type Dispatch, type SetStateAction } from "react";
import type { Book, Track } from "@shared/types";
import {
  buildMusicTrackList,
  musicQueueFrom,
  nextMusicIndex,
  previousMusicIndex,
  shuffleMusicTracks,
  type MusicListTrack,
} from "@shared/musicList";
import {
  isMusicDownloadActive,
  progressForTrack as downloadProgressForTrack,
} from "@shared/musicDownloadState";
import { shouldRestartCurrentMusicTrack } from "@shared/musicPlayback";
import type { useMusicDownload } from "./useMusicDownload";
import { strings } from "../i18n/strings";

type SessionState = "Unauthenticated" | "AuthenticatedOnline" | "AuthenticatedOffline";

interface UseMusicPlaybackOptions {
  books: Book[];
  allTracks: Track[];
  musicTracks: MusicListTrack[];
  setMusicTracks: Dispatch<SetStateAction<MusicListTrack[]>>;
  setTracks: Dispatch<SetStateAction<Track[]>>;
  sessionState: SessionState;
  refreshLibrary: () => Promise<void>;
  musicDownload: ReturnType<typeof useMusicDownload>;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  seekTo: (fraction: number) => void;
  currentTrack: Track | null;
  positionMs: number;
}

export function useMusicPlayback({
  books,
  allTracks,
  musicTracks,
  setMusicTracks,
  setTracks,
  sessionState,
  refreshLibrary,
  musicDownload,
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

  const ensureTrackLocal = useCallback(
    async (
      bookId: string,
      trackId: string,
      options?: { showProgress?: boolean; bulkDownloaded?: number; bulkTotal?: number },
    ): Promise<Track | null> => {
      let bookTracks = await window.tonezen.db.getTracks(bookId);
      let track = bookTracks.find((item) => item.id === trackId);
      if (track?.localPath) return track as Track;

      if (sessionState === "AuthenticatedOffline") {
        setMusicError(strings.musicPlaybackErrorOffline);
        return null;
      }
      if (sessionState === "Unauthenticated") {
        setMusicError(strings.musicPlaybackErrorLogin);
        return null;
      }

      const showProgress = options?.showProgress ?? true;
      let progressInterval: ReturnType<typeof setInterval> | undefined;
      let simulatedProgress = 0.05;

      const tickProgress = () => {
        simulatedProgress = Math.min(0.92, simulatedProgress + 0.06);
        if (options?.bulkTotal != null && options.bulkDownloaded != null) {
          musicDownload.updateBulk(options.bulkDownloaded, options.bulkTotal, trackId, simulatedProgress);
        } else if (showProgress) {
          musicDownload.updateTrack(trackId, simulatedProgress);
        }
      };

      if (showProgress) {
        if (options?.bulkTotal != null && options.bulkDownloaded != null) {
          musicDownload.updateBulk(options.bulkDownloaded, options.bulkTotal, trackId, simulatedProgress);
        } else {
          musicDownload.beginTrack(trackId);
        }
        progressInterval = setInterval(tickProgress, 250);
      }

      try {
        await window.tonezen.download.track(bookId, trackId);
        bookTracks = await window.tonezen.db.getTracks(bookId);
        track = bookTracks.find((item) => item.id === trackId);
        await refreshLibrary();
        return (track as Track) ?? null;
      } catch {
        setMusicError(strings.musicPlaybackErrorDownload);
        return null;
      } finally {
        if (progressInterval) clearInterval(progressInterval);
        if (showProgress && options?.bulkTotal == null) {
          musicDownload.finishTrack();
        }
      }
    },
    [musicDownload, refreshLibrary, sessionState],
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
      if (isMusicDownloadActive(musicDownload.state)) return;
      const index = queue.findIndex((item) => item.trackId === currentTrackId);
      if (index < 0 || queue.length <= 1) return;
      const next = queue[nextMusicIndex(index, queue.length)];
      if (!next || next.isDownloaded) return;

      const jobId = prefetchJobRef.current + 1;
      prefetchJobRef.current = jobId;
      void (async () => {
        await ensureTrackLocal(next.bookId, next.trackId, { showProgress: false });
        if (prefetchJobRef.current !== jobId) return;
      })();
    },
    [ensureTrackLocal, musicDownload.state],
  );

  const playMusicTrackInternal = useCallback(
    async (
      listTrack: MusicListTrack,
      options?: { showDownloadProgress?: boolean; allowWhileDownloading?: boolean },
    ) => {
      if (deletingTrackIdRef.current === listTrack.trackId) return false;
      if (!options?.allowWhileDownloading && isMusicDownloadActive(musicDownload.state)) {
        return false;
      }

      const book = books.find((item) => item.id === listTrack.bookId);
      if (!book) return false;

      if (
        musicMode &&
        currentTrack?.id === listTrack.trackId &&
        !downloadProgressForTrack(musicDownload.state, listTrack.trackId)
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
          showProgress: showDownloadProgress,
        });
      }

      if (!local?.localPath) return false;

      const queue = musicQueueFrom(musicTracks, listTrack.trackId);
      musicQueueRef.current = queue;
      setMusicQueue(queue);
      setTracks([local]);
      playTrack(local, 0, book);
      prefetchNextTrack(queue, listTrack.trackId);
      return true;
    },
    [
      books,
      currentTrack?.id,
      ensureTrackLocal,
      musicDownload,
      musicMode,
      musicTracks,
      pauseOrResume,
      playTrack,
      prefetchNextTrack,
      resolveLocalTrack,
      setTracks,
    ],
  );

  const onMusicTabSelected = useCallback(() => {
    if (musicStartedInSessionRef.current || musicTracks.length > 0) return;
    const built = buildMusicTrackList(books, allTracks);
    if (built.length === 0) return;
    setMusicTracks(shuffleMusicTracks(built));
  }, [allTracks, books, musicTracks.length, setMusicTracks]);

  const handleSkipNext = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void playMusicTrackInternal(queue[nextMusicIndex(index, queue.length)]);
    return true;
  }, [currentTrack, musicMode, playMusicTrackInternal]);

  const handleSkipPrevious = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    if (shouldRestartCurrentMusicTrack(positionMs)) {
      seekTo(0);
      return true;
    }
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void playMusicTrackInternal(queue[previousMusicIndex(index, queue.length)]);
    return true;
  }, [currentTrack, musicMode, playMusicTrackInternal, positionMs, seekTo]);

  const handleTrackEnded = useCallback(() => {
    const queue = musicQueueRef.current;
    if (!musicMode || queue.length === 0 || !currentTrack) return false;
    const index = queue.findIndex((item) => item.trackId === currentTrack.id);
    if (index < 0) return false;
    void playMusicTrackInternal(queue[nextMusicIndex(index, queue.length)], {
      showDownloadProgress: false,
    });
    return true;
  }, [currentTrack, musicMode, playMusicTrackInternal]);

  const downloadAllMusic = useCallback(async () => {
    if (isMusicDownloadActive(musicDownload.state)) return;
    const total = musicTracks.length;
    const pending = musicTracks.filter((track) => !track.isDownloaded);
    if (pending.length === 0) return;

    let done = musicTracks.filter((track) => track.isDownloaded).length;
    musicDownload.beginBulk(done, total);
    for (const track of pending) {
      const result = await ensureTrackLocal(track.bookId, track.trackId, {
        showProgress: true,
        bulkDownloaded: done,
        bulkTotal: total,
      });
      if (result?.localPath) {
        done += 1;
        musicDownload.incrementBulk(done, total);
      }
    }
    musicDownload.clear();
    await refreshLibrary();
  }, [ensureTrackLocal, musicDownload, musicTracks, refreshLibrary]);

  const downloadMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {
      if (isMusicDownloadActive(musicDownload.state) || listTrack.isDownloaded) return;
      setMusicError(null);
      await ensureTrackLocal(listTrack.bookId, listTrack.trackId);
    },
    [ensureTrackLocal, musicDownload.state],
  );

  const deleteMusicTrack = useCallback(
    async (listTrack: MusicListTrack) => {
      if (isMusicDownloadActive(musicDownload.state)) return;

      const trackId = listTrack.trackId;
      deletingTrackIdRef.current = trackId;
      prefetchJobRef.current += 1;

      try {
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
    [currentTrack?.id, musicDownload.state, refreshLibrary, stopPlayback],
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
    musicDownload.clear();
    setMusicError(null);
  }, [musicDownload]);

  return {
    musicMode,
    setMusicMode,
    musicPlaybackBook,
    musicError,
    musicQueue,
    musicStartedInSessionRef,
    musicQueueRef,
    playMusicTrack: playMusicTrackInternal,
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
