import { useCallback, useState, type Dispatch, type SetStateAction } from "react";
import type { AudiobookProgress, Book, Cycle, SessionState, Track } from "@core/types";
import { nextChapterInBook } from "@core/downloads/audiobookDownloadTarget";
import { resolveAudiobookPlaybackIntent } from "@core/playback/audiobookPlaybackIntent";
import {
  orderedCycleEntriesFromResume,
  resolveCycleResumeTarget,
} from "@core/playback/cycleListenProgress";
import { CyclePlaybackResolver } from "@core/playback/cyclePlayback";
import { completedAudiobookProgress, upsertAudiobookProgress } from "@core/progress/audiobookProgress";
import { getTonezenApi, useSaveProgressMutation } from "@/shared/api";
import { logDownloadFailure } from "@/shared/lib/diagnostics";
import type { DownloadQueueApi, RefreshLibraryOptions } from "@/shared/api";

const cycleResolver = new CyclePlaybackResolver();

interface AudiobookSessionMusicHandlers {
  setMusicMode: (value: boolean) => void;
  handleSkipNext: () => boolean;
  handleSkipPrevious: () => boolean;
  handleTrackEnded: () => boolean;
}

interface UseAudiobookSessionOptions {
  sessionState: SessionState;
  books: Book[];
  cycles: Cycle[];
  selectedBook: Book | null;
  setSelectedBook: (book: Book | null) => void;
  selectedCycle: Cycle | null;
  setSelectedCycle: (cycle: Cycle | null) => void;
  tracks: Track[];
  setTracks: (tracks: Track[]) => void;
  tracksByBookId: Map<string, Track[]>;
  progressByBook: Map<string, AudiobookProgress>;
  setProgressList: Dispatch<SetStateAction<AudiobookProgress[]>>;
  refreshLibrary: (options?: RefreshLibraryOptions) => Promise<void>;
  downloadQueue: DownloadQueueApi;
  playTrack: (track: Track, startMs?: number, book?: Book | null) => void;
  stopPlayback: () => void;
  pauseOrResume: () => void;
  currentTrack: Track | null;
  durationMs: number;
  isPlaying: boolean;
  showToast: (message: string) => void;
  closeExpandedPlayer: () => void;
  music: AudiobookSessionMusicHandlers;
}

export function useAudiobookSession({
  sessionState,
  books,
  cycles,
  selectedBook,
  setSelectedBook,
  selectedCycle,
  setSelectedCycle,
  tracks,
  setTracks,
  tracksByBookId,
  progressByBook,
  setProgressList,
  refreshLibrary,
  downloadQueue,
  playTrack,
  stopPlayback,
  pauseOrResume,
  currentTrack,
  durationMs,
  isPlaying,
  showToast,
  closeExpandedPlayer,
  music,
}: UseAudiobookSessionOptions) {
  const api = getTonezenApi();
  const saveProgress = useSaveProgressMutation();
  const [cyclePlayingId, setCyclePlayingId] = useState<string | null>(null);
  const [earlierChapterPrompt, setEarlierChapterPrompt] = useState<Track | null>(null);

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
    async (book: Book, bookTracks: Track[], track: Track, startMs: number) => {
      music.setMusicMode(false);
      const local = track.localPath ? track : await ensureAudiobookTrackLocal(book.id, track.id);
      if (local?.localPath) {
        playTrack(local, startMs, book);
        closeExpandedPlayer();
        void prefetchNextAudiobookChapter(book, bookTracks, local);
      } else if (!track.localPath) {
        const offlineMessage =
          sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated"
            ? "Нет сети — нужен интернет для первой загрузки"
            : "Не удалось скачать";
        showToast(offlineMessage);
        stopPlayback();
      }
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

  const playBookTrack = useCallback(
    async (track: Track) => {
      if (!selectedBook) return;
      const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
      const saved = progressByBook.get(selectedBook.id) ?? null;
      const intent = resolveAudiobookPlaybackIntent(sortedTracks, saved, track);
      if (intent.kind === "ConfirmEarlierChapter") {
        setEarlierChapterPrompt(track);
        return;
      }
      const startMs = intent.kind === "Resume" ? intent.positionMs : 0;
      await playAudiobookTrackResolved(selectedBook, tracks, track, startMs);
    },
    [playAudiobookTrackResolved, progressByBook, selectedBook, tracks],
  );

  const playCycle = useCallback(
    async (cycle: Cycle) => {
      if (cyclePlayingId === cycle.id && isPlaying) {
        pauseOrResume();
        return;
      }
      setCyclePlayingId(cycle.id);
      music.setMusicMode(false);
      const resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressByBook);
      if (!resume) {
        showToast("В цикле нет доступных глав для воспроизведения");
        setCyclePlayingId(null);
        return;
      }
      if (!resume.track.localPath && sessionState !== "AuthenticatedOnline") {
        showToast("Нет сети — нужен интернет для первой загрузки");
        stopPlayback();
        setCyclePlayingId(null);
        return;
      }
      const local = resume.track.localPath
        ? resume.track
        : await ensureAudiobookTrackLocal(resume.book.id, resume.track.id);
      if (!local?.localPath) {
        showToast("Не удалось скачать");
        stopPlayback();
        setCyclePlayingId(null);
        return;
      }
      setSelectedBook(resume.book);
      setSelectedCycle(cycle);
      const bookTracks = await api.db.getTracks(resume.book.id);
      setTracks(bookTracks);
      playTrack(local, resume.startPositionMs, resume.book);
      const entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, {
        book: resume.book,
        track: local,
        startPositionMs: resume.startPositionMs,
      });
      if (entries.length > 1 && sessionState === "AuthenticatedOnline") {
        const next = entries[1];
        if (!next.track.localPath) {
          void downloadQueue
            .enqueue({
              bookId: next.book.id,
              trackId: next.track.id,
              priority: "PREFETCH",
              title: next.track.title,
              subtitle: next.book.title,
              contentType: next.book.contentType,
            })
            .catch(() => {});
        }
      }
    },
    [
      api,
      cyclePlayingId,
      downloadQueue,
      ensureAudiobookTrackLocal,
      isPlaying,
      music,
      pauseOrResume,
      playTrack,
      progressByBook,
      sessionState,
      setSelectedBook,
      setSelectedCycle,
      setTracks,
      showToast,
      stopPlayback,
      tracksByBookId,
    ],
  );

  const markBookListened = useCallback(
    async (book: Book, listened: boolean) => {
      const bookTracks = await api.db.getTracks(book.id);
      if (bookTracks.length === 0) return;
      const track = listened ? bookTracks[bookTracks.length - 1] : bookTracks[0];
      await saveProgress.mutateAsync({
        bookId: book.id,
        trackId: track.id,
        positionMs: listened ? track.durationMs ?? 0 : 0,
      });
      await refreshLibrary();
    },
    [api, refreshLibrary, saveProgress],
  );

  const markTrackListened = useCallback(
    async (book: Book, track: Track, listened: boolean) => {
      await saveProgress.mutateAsync({
        bookId: book.id,
        trackId: track.id,
        positionMs: listened ? track.durationMs ?? 0 : 0,
      });
      await refreshLibrary();
      if (selectedBook?.id === book.id) {
        setTracks(await api.db.getTracks(book.id));
      }
    },
    [api, refreshLibrary, saveProgress, selectedBook, setTracks],
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

  const handleSkipNext = useCallback(() => {
    if (music.handleSkipNext()) return;
    if (!currentTrack || !selectedBook) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next) void playBookTrack(next);
  }, [currentTrack, music, playBookTrack, selectedBook, tracks]);

  const handleSkipPrevious = useCallback(() => {
    if (music.handleSkipPrevious()) return;
    if (!currentTrack || !selectedBook) return;
    const prev = cycleResolver.previousInBook(currentTrack, tracks);
    if (prev) void playBookTrack(prev);
  }, [currentTrack, music, playBookTrack, selectedBook, tracks]);

  const handleTrackEnded = useCallback(() => {
    const completedProgress = completedAudiobookProgress(selectedBook, currentTrack, durationMs);
    if (completedProgress) {
      setProgressList((current) => upsertAudiobookProgress(current, completedProgress));
      void saveProgress.mutate({
        bookId: completedProgress.bookId,
        trackId: completedProgress.trackId,
        positionMs: completedProgress.positionMs,
      });
    }

    if (music.handleTrackEnded()) return;
    if (!currentTrack || !selectedBook) return;

    const nextInBook = cycleResolver.nextInBook(currentTrack, tracks);
    if (nextInBook) {
      void advanceAudiobookTrack(selectedBook, tracks, nextInBook);
      return;
    }

    const cycle =
      selectedCycle ?? cycles.find((item) => item.books.some((book) => book.id === selectedBook.id));
    if (!cycle) return;

    const booksBySlug = new Map(cycle.books.map((book) => [book.slug, book]));
    const result = cycleResolver.nextInCycle(
      selectedBook,
      currentTrack,
      cycle,
      booksBySlug,
      tracksByBookId,
    );
    if (!result.book || !result.track) return;

    void (async () => {
      setSelectedBook(result.book as Book);
      const bookTracks = await api.db.getTracks(result.book!.id);
      setTracks(bookTracks);
      if (!selectedCycle && result.isNextBookInCycle) {
        setSelectedCycle(cycle);
      }
      void advanceAudiobookTrack(result.book as Book, bookTracks, result.track as Track);
    })();
  }, [
    advanceAudiobookTrack,
    api,
    currentTrack,
    cycles,
    durationMs,
    music,
    selectedBook,
    selectedCycle,
    setProgressList,
    setSelectedBook,
    setSelectedCycle,
    setTracks,
    tracks,
    tracksByBookId,
  ]);

  const continueBook = useCallback(async () => {
    if (!selectedBook) return;
    const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const saved = progressByBook.get(selectedBook.id);
    const track = saved
      ? sortedTracks.find((item) => item.id === saved.trackId) ?? sortedTracks[0]
      : sortedTracks[0];
    if (track) await playBookTrack(track);
  }, [playBookTrack, progressByBook, selectedBook, tracks]);

  const dismissEarlierChapterPrompt = useCallback(() => setEarlierChapterPrompt(null), []);

  const confirmEarlierChapterPrompt = useCallback(() => {
    const track = earlierChapterPrompt;
    setEarlierChapterPrompt(null);
    if (track && selectedBook) void playAudiobookTrackResolved(selectedBook, tracks, track, 0);
  }, [earlierChapterPrompt, playAudiobookTrackResolved, selectedBook, tracks]);

  return {
    cyclePlayingId,
    earlierChapterPrompt,
    dismissEarlierChapterPrompt,
    confirmEarlierChapterPrompt,
    playBookTrack,
    playCycle,
    continueBook,
    markBookListened,
    markTrackListened,
    handleSkipNext,
    handleSkipPrevious,
    handleTrackEnded,
  };
}
