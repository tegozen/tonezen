import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Book, Cycle, Track } from "@core/types";
import {
  buildMusicTrackListForCatalogUpdate,
  visibleMusicTrackList,
  type MusicListTrack,
} from "@core/catalog/musicList";
import { completedAudiobookProgress, upsertAudiobookProgress } from "@core/progress/audiobookProgress";
import { completedDownloadItems } from "@core/downloads/downloadsPageState";
import { progressForTrack } from "@core/downloads/downloadQueueState";
import { nextChapterInBook } from "@core/downloads/audiobookDownloadTarget";
import { resolveAudiobookPlaybackIntent } from "@core/playback/audiobookPlaybackIntent";
import {
  orderedCycleEntriesFromResume,
  resolveCycleResumeTarget,
} from "@core/playback/cycleListenProgress";
import { CyclePlaybackResolver } from "@core/playback/cyclePlayback";
import { findActiveMusicTrack } from "@core/playback/musicPlayback";
import { AppShell } from "@/widgets/app-shell";
import { LibraryFilterSheet } from "@/features/library-filter";
import { LoginView } from "@/pages/login";
import { NowPlayingSheet } from "@/widgets/now-playing";
import { ToastMessage } from "@/shared/ui/ToastMessage";
import { useDownloadQueue } from "@/features/downloads";
import { useMusicPlayback } from "@/features/music-queue";
import { usePlayback } from "@/features/playback";
import { useTonezenSession } from "@/features/auth";
import type { BottomTab, LibraryFilter } from "@core/platform/navigation";
import {
  buildTracksByBookId,
  computeCycleCardState,
  filterAndSortCycles,
  isBookFullyDownloaded,
} from "@/entities/cycle";
import { BookDetailPage } from "@/pages/book-detail";
import { CycleDetailPage } from "@/pages/cycle-detail";
import { DownloadsPage } from "@/pages/downloads";
import { LibraryPage } from "@/pages/library";
import { ProfilePage } from "@/pages/profile";

const cycleResolver = new CyclePlaybackResolver();
const defaultFilter: LibraryFilter = { contentFilter: "all", sortOrder: "recent" };
type RefreshLibraryOptions = { rebuildMusic?: boolean; reconcileLocalPaths?: boolean };

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function App() {
  const session = useTonezenSession();
  const {
    sessionState,
    userEmail,
    displayName,
    avatarUrl,
    memberSinceEpochMs,
    email,
    setEmail,
    password,
    setPassword,
    error,
    login,
    verifyInviteCode,
    registerWithInvite,
    requestPasswordRecovery,
    logout,
    refreshSession,
  } = session;

  const [activeTab, setActiveTab] = useState<BottomTab>("music");
  const [cycles, setCycles] = useState<Cycle[]>([]);
  const [books, setBooks] = useState<Book[]>([]);
  const [allTracks, setAllTracks] = useState<Track[]>([]);
  const [selectedCycle, setSelectedCycle] = useState<Cycle | null>(null);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<LibraryFilter>(defaultFilter);
  const [showFilterSheet, setShowFilterSheet] = useState(false);
  const [showExpandedPlayer, setShowExpandedPlayer] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [storageUsed, setStorageUsed] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [lastSyncAtEpochMs, setLastSyncAtEpochMs] = useState<number | null>(null);
  const [showSignOutConfirm, setShowSignOutConfirm] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [musicTracks, setMusicTracks] = useState<MusicListTrack[]>([]);
  const [cyclePlayingId, setCyclePlayingId] = useState<string | null>(null);
  const [progressList, setProgressList] = useState<Array<{ bookId: string; trackId: string; positionMs: number; updatedAt: string }>>([]);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [earlierChapterPrompt, setEarlierChapterPrompt] = useState<Track | null>(null);

  const downloadQueue = useDownloadQueue();
  const musicStartedInSessionRef = useRef(false);
  const refreshLibraryRef = useRef<(options?: RefreshLibraryOptions) => Promise<void>>(async () => {});
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = useCallback((message: string) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToastMessage(message);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
      toastTimerRef.current = null;
    }, 3_500);
  }, []);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    };
  }, []);

  const logDownloadFailure = useCallback(
    (input: {
      code: string;
      bookId: string;
      trackId: string;
      bookTitle?: string;
      trackTitle?: string;
      details?: string;
    }) => {
      void window.tonezen.diagnostics
        .logError({
          area: "download",
          message: "Не удалось скачать",
          ...input,
        })
        .catch(() => {});
    },
    [],
  );

  const refreshLibrary = useCallback(async (options?: RefreshLibraryOptions) => {
    const rebuildMusic = options?.rebuildMusic ?? true;
    const reconcileLocalPaths = options?.reconcileLocalPaths ?? true;
    const [library, stats, sync, progress] = await Promise.all([
      window.tonezen.db.getLibrarySnapshot({ reconcileLocalPaths }),
      window.tonezen.download.storageStats(),
      window.tonezen.sync.status(),
      window.tonezen.db.getAllProgress(),
    ]);
    setCycles(library.cycles as Cycle[]);
    setBooks(library.books as Book[]);
    setAllTracks(library.tracks as Track[]);
    setStorageUsed(stats.usedBytes);
    setPendingCount(sync.pendingCount);
    setLastSyncAtEpochMs(sync.lastSyncAtEpochMs);
    setProgressList(progress);
    if (rebuildMusic) {
      setMusicTracks((current) =>
        buildMusicTrackListForCatalogUpdate(
          current,
          library.books as Book[],
          library.tracks as Track[],
          musicStartedInSessionRef.current,
        ),
      );
    }
    setIsLoading(false);
  }, []);

  refreshLibraryRef.current = refreshLibrary;

  const musicHandlersRef = useRef<{
    handleSkipNext: () => boolean;
    handleSkipPrevious: () => boolean;
    handleTrackEnded: () => boolean;
  }>({
    handleSkipNext: () => false,
    handleSkipPrevious: () => false,
    handleTrackEnded: () => false,
  });
  const skipHandlers = useMemo(
    () => ({
      onSkipNext: () => musicHandlersRef.current.handleSkipNext(),
      onSkipPrevious: () => musicHandlersRef.current.handleSkipPrevious(),
    }),
    [],
  );

  const skipTracks = useMemo(() => tracks, [tracks]);

  const {
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    pauseOrResume,
    seekBy,
    seekTo,
    volume,
    setVolume,
  } = usePlayback(selectedBook, tracks, skipTracks, skipHandlers);

  const music = useMusicPlayback({
    books,
    allTracks,
    musicTracks,
    setMusicTracks,
    setTracks,
    sessionState,
    refreshLibrary: () => refreshLibraryRef.current(),
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    seekTo,
    currentTrack,
    positionMs,
  });

  musicHandlersRef.current = {
    handleSkipNext: music.handleSkipNext,
    handleSkipPrevious: music.handleSkipPrevious,
    handleTrackEnded: music.handleTrackEnded,
  };
  musicStartedInSessionRef.current = music.musicStartedInSessionRef.current;

  const tracksByBookId = useMemo(() => buildTracksByBookId(allTracks), [allTracks]);

  const downloadedBookIds = useMemo(() => {
    const ids = new Set<string>();
    for (const book of books) {
      if (isBookFullyDownloaded(book.id, tracksByBookId)) ids.add(book.id);
    }
    return ids;
  }, [books, tracksByBookId]);

  const progressByBook = useMemo(
    () => new Map(progressList.map((p) => [p.bookId, p])),
    [progressList],
  );

  const cycleCardStateById = useMemo(() => {
    const map: Record<string, ReturnType<typeof computeCycleCardState>> = {};
    for (const cycle of cycles) {
      map[cycle.id] = computeCycleCardState(cycle, downloadedBookIds, tracksByBookId, progressByBook);
    }
    return map;
  }, [cycles, downloadedBookIds, tracksByBookId, progressByBook]);

  const filteredCycles = useMemo(
    () => filterAndSortCycles(cycles, query, filter, downloadedBookIds, progressByBook),
    [cycles, query, filter, downloadedBookIds, progressByBook],
  );

  useEffect(() => {
    if (sessionState === "Unauthenticated") return;
    if (sessionState === "AuthenticatedOffline") {
      void refreshLibrary({ rebuildMusic: true, reconcileLocalPaths: false }).then(() =>
        refreshLibrary({ rebuildMusic: true, reconcileLocalPaths: true }),
      );
      return;
    }
    setIsLoading(true);
    const sync = window.tonezen.catalog.sync().then(
      () => true,
      () => false,
    );
    void refreshLibrary({ rebuildMusic: true, reconcileLocalPaths: false })
      .then(() => sync)
      .then(() => refreshLibrary({ rebuildMusic: true, reconcileLocalPaths: true }))
      .catch(() => refreshLibrary({ rebuildMusic: true, reconcileLocalPaths: true }));
  }, [sessionState, refreshLibrary]);

  useEffect(() => {
    if (sessionState === "Unauthenticated" || sessionState === "AuthenticatedOffline") return;
    return window.tonezen.catalog.onUpdated(() => {
      void refreshLibrary({ rebuildMusic: true });
    });
  }, [sessionState, refreshLibrary]);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const unsubscribe = window.tonezen.download.onQueueState((state) => {
      if (!state.activeTrackId && state.queuedItems.length === 0) {
        clearTimeout(timer);
        timer = setTimeout(() => {
          void refreshLibrary();
        }, 300);
      }
    });
    return () => {
      clearTimeout(timer);
      unsubscribe();
    };
  }, [refreshLibrary]);

  useEffect(() => {
    return window.tonezen.download.onFailed(() => {
      showToast("Не удалось скачать");
    });
  }, [showToast]);

  const syncCatalog = async () => {
    setIsLoading(true);
    try {
      await window.tonezen.catalog.sync();
      await refreshLibrary({ rebuildMusic: true });
    } catch {
      await refreshLibrary({ rebuildMusic: true });
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogin = async () => {
    const ok = await login();
    if (ok) {
      await syncCatalog();
    }
  };

  const handleLogout = async () => {
    stopPlayback();
    setSelectedBook(null);
    setSelectedCycle(null);
    music.resetMusicSession();
    musicStartedInSessionRef.current = false;
    await logout();
  };

  const ensureAudiobookTrackLocal = async (bookId: string, trackId: string): Promise<Track | null> => {
    let bookTracks = await window.tonezen.db.getTracks(bookId);
    let track = bookTracks.find((item) => item.id === trackId);
    if (track?.localPath) return track as Track;
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
      bookTracks = await window.tonezen.db.getTracks(bookId);
      track = bookTracks.find((item) => item.id === trackId);
      await refreshLibrary();
      return (track as Track) ?? null;
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
  };

  const openBook = async (book: Book, fromCycle: Cycle | null = selectedCycle) => {
    setSelectedBook(book);
    music.setMusicMode(false);
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    setTracks(bookTracks as Track[]);
    if (fromCycle) setSelectedCycle(fromCycle);
  };

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

  const playAudiobookTrackResolved = async (book: Book, bookTracks: Track[], track: Track, startMs: number) => {
    music.setMusicMode(false);
    const local = track.localPath ? track : await ensureAudiobookTrackLocal(book.id, track.id);
    if (local?.localPath) {
      playTrack(local, startMs, book);
      setShowExpandedPlayer(false);
      void prefetchNextAudiobookChapter(book, bookTracks, local);
    } else if (!track.localPath) {
      const offlineMessage =
        sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated"
          ? "Нет сети — нужен интернет для первой загрузки"
          : "Не удалось скачать";
      showToast(offlineMessage);
      stopPlayback();
    }
  };

  const playBookTrack = async (track: Track) => {
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
  };

  const playCycle = async (cycle: Cycle) => {
    if (cyclePlayingId === cycle.id && isPlaying) {
      pauseOrResume();
      return;
    }
    setCyclePlayingId(cycle.id);
    music.setMusicMode(false);
    const progressMap = new Map(
      progressList.map((entry) => [
        entry.bookId,
        {
          bookId: entry.bookId,
          trackId: entry.trackId,
          positionMs: entry.positionMs,
          updatedAt: entry.updatedAt,
        },
      ]),
    );
    const resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressMap);
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
    const bookTracks = await window.tonezen.db.getTracks(resume.book.id);
    setTracks(bookTracks as Track[]);
    playTrack(local, resume.startPositionMs, resume.book);
    const entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, {
      book: resume.book,
      track: local,
      startPositionMs: resume.startPositionMs,
    });
    if (entries.length > 1 && sessionState === "AuthenticatedOnline") {
      const next = entries[1];
      if (!next.track.localPath) {
        void downloadQueue.enqueue({
          bookId: next.book.id,
          trackId: next.track.id,
          priority: "PREFETCH",
          title: next.track.title,
          subtitle: next.book.title,
          contentType: next.book.contentType,
        }).catch(() => {});
      }
    }
  };

  const downloadAllBookTracks = async (book: Book) => {
    const bookTracks = await window.tonezen.db.getTracks(book.id);
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
        showToast(message === "__download_auth_required__" ? "Войдите в аккаунт, чтобы скачать трек" : "Не удалось скачать");
      }
    }
  };

  const downloadBookTrack = async (book: Book, track: Track) => {
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
  };

  const downloadCycle = async (cycle: Cycle) => {
    const batchId = crypto.randomUUID();
    const requests = [];
    for (const book of cycle.books) {
      const bookTracks = await window.tonezen.db.getTracks(book.id);
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
  };

  const removeBookDownloads = async (book: Book) => {
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    if (currentTrack && bookTracks.some((t) => t.id === currentTrack.id)) {
      stopPlayback();
      await delay(50);
    }
    for (const track of bookTracks) {
      if (track.localPath) {
        await window.tonezen.download.delete(book.id, track.id);
      }
    }
    await refreshLibrary();
    if (selectedBook?.id === book.id) {
      setTracks((await window.tonezen.db.getTracks(book.id)) as Track[]);
    }
  };

  const markBookListened = async (book: Book, listened: boolean) => {
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    if (bookTracks.length === 0) return;
    const track = listened ? bookTracks[bookTracks.length - 1] : bookTracks[0];
    await window.tonezen.progress.save(book.id, track.id, listened ? track.durationMs ?? 0 : 0);
    await refreshLibrary();
  };

  const handleSkipNext = () => {
    if (music.handleSkipNext()) return;
    if (!currentTrack || !selectedBook) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next) void playBookTrack(next);
  };

  const handleSkipPrevious = () => {
    if (music.handleSkipPrevious()) return;
    if (!currentTrack || !selectedBook) return;
    const prev = cycleResolver.previousInBook(currentTrack, tracks);
    if (prev) void playBookTrack(prev);
  };

  const advanceAudiobookTrack = async (book: Book, bookTracks: Track[], nextTrack: Track) => {
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
  };

  const handleTrackEnded = () => {
    const completedProgress = completedAudiobookProgress(selectedBook, currentTrack, durationMs);
    if (completedProgress) {
      setProgressList((current) => upsertAudiobookProgress(current, completedProgress));
      void window.tonezen.progress.save(
        completedProgress.bookId,
        completedProgress.trackId,
        completedProgress.positionMs,
      );
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
      const bookTracks = await window.tonezen.db.getTracks(result.book!.id);
      setTracks(bookTracks as Track[]);
      if (!selectedCycle && result.isNextBookInCycle) {
        setSelectedCycle(cycle);
      }
      void advanceAudiobookTrack(result.book as Book, bookTracks as Track[], result.track as Track);
    })();
  };

  const continueBook = async () => {
    if (!selectedBook) return;
    const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
    const saved = progressByBook.get(selectedBook.id);
    const track = saved
      ? sortedTracks.find((item) => item.id === saved.trackId) ?? sortedTracks[0]
      : sortedTracks[0];
    if (track) await playBookTrack(track);
  };

  const markTrackListened = async (book: Book, track: Track, listened: boolean) => {
    await window.tonezen.progress.save(book.id, track.id, listened ? track.durationMs ?? 0 : 0);
    await refreshLibrary();
    if (selectedBook?.id === book.id) {
      setTracks((await window.tonezen.db.getTracks(book.id)) as Track[]);
    }
  };

  const removeTrackDownload = async (book: Book, track: Track) => {
    if (!track.localPath) return;
    if (currentTrack?.id === track.id) {
      stopPlayback();
      await delay(50);
    }
    await window.tonezen.download.delete(book.id, track.id);
    await refreshLibrary();
    if (selectedBook?.id === book.id) {
      setTracks((await window.tonezen.db.getTracks(book.id)) as Track[]);
    }
  };

  const deleteAllDownloads = async () => {
    stopPlayback();
    setShowExpandedPlayer(false);
    await delay(50);
    await downloadQueue.cancelAll();
    await window.tonezen.download.deleteAll();
    await refreshLibrary();
  };

  const savedBookProgress = selectedBook ? progressByBook.get(selectedBook.id) : undefined;

  const miniTitle = currentTrack?.title ?? null;
  const activeMusicTrack = findActiveMusicTrack(
    music.musicQueue,
    music.musicQueueRef.current,
    currentTrack?.id,
  );
  const visibleMusicTracks = useMemo(
    () => visibleMusicTrackList(musicTracks, sessionState === "AuthenticatedOnline"),
    [musicTracks, sessionState],
  );
  const completedDownloads = useMemo(
    () => completedDownloadItems(downloadQueue.state, allTracks, books),
    [allTracks, books, downloadQueue.state],
  );
  const miniSubtitle = music.musicMode
    ? activeMusicTrack
      ? [activeMusicTrack.artist, activeMusicTrack.albumTitle].filter(Boolean).join(" · ") ||
        "Сейчас играет"
      : "Сейчас играет"
    : selectedBook?.author ?? "Сейчас играет";
  const miniDownloadProgress = currentTrack
    ? progressForTrack(downloadQueue.state, currentTrack.id)
    : null;
  const currentTrackInSelectedBook =
    selectedBook != null &&
    currentTrack != null &&
    tracks.some((track) => track.id === currentTrack.id);
  const showMiniPlayer =
    Boolean(currentTrack) &&
    (music.musicMode || currentTrackInSelectedBook || (!selectedBook && !selectedCycle));
  const handleMusicTabSelected = music.onMusicTabSelected;
  const handleTabSelect = useCallback(
    (tab: BottomTab) => {
      setActiveTab(tab);
      if (tab !== "books") setShowFilterSheet(false);
      if (tab === "music") handleMusicTabSelected();
      if (tab === "profile") void refreshSession();
    },
    [handleMusicTabSelected, refreshSession],
  );

  useEffect(() => {
    if (activeTab === "music") {
      handleMusicTabSelected();
    }
  }, [activeTab, handleMusicTabSelected]);

  const showBottomNav = !selectedBook && !selectedCycle;
  const coverSeed = currentTrack?.id ?? selectedBook?.id ?? "";

  if (sessionState === "Unauthenticated") {
    return (
      <>
        <LoginView
          email={email}
          password={password}
          error={error}
          onEmailChange={setEmail}
          onPasswordChange={setPassword}
          onLogin={() => void handleLogin()}
          onVerifyInviteCode={verifyInviteCode}
          onSignup={registerWithInvite}
          onPasswordRecovery={requestPasswordRecovery}
        />
        <audio ref={audioRef} className="hidden" onEnded={handleTrackEnded} onTimeUpdate={onTimeUpdate} />
      </>
    );
  }

  const shell = (
    <AppShell
      activeTab={activeTab}
      onTabSelect={handleTabSelect}
      miniTitle={miniTitle}
      miniSubtitle={miniSubtitle}
      coverSeed={coverSeed}
      isPlaying={isPlaying}
      positionMs={positionMs}
      durationMs={durationMs}
      showMiniPlayer={showMiniPlayer}
      showBottomNav={showBottomNav}
      miniDownloadProgress={miniDownloadProgress}
      onMiniBarClick={() => setShowExpandedPlayer(true)}
      onMiniPlayPause={() => {
        music.onMiniPlayerPlayPause(activeMusicTrack);
      }}
    >
      {selectedBook ? (
        <BookDetailPage
          book={selectedBook}
          tracks={tracks}
          currentTrackId={
            !music.musicMode && currentTrack && tracks.some((track) => track.id === currentTrack.id)
              ? currentTrack.id
              : null
          }
          playbackPositionMs={positionMs}
          downloadQueue={downloadQueue.state}
          onBack={() => {
            setSelectedBook(null);
          }}
          onTrackClick={(track) => void playBookTrack(track)}
          onDownloadRequest={() => void downloadAllBookTracks(selectedBook)}
          onDownloadTrack={(track) => void downloadBookTrack(selectedBook, track)}
          onToggleBookListened={() => {
            const listened = tracks.length > 0 && tracks.every((track) => {
              const saved = progressByBook.get(selectedBook.id);
              if (!saved) return false;
              return track.sortOrder < (tracks.find((item) => item.id === saved.trackId)?.sortOrder ?? Infinity)
                || (track.id === saved.trackId && saved.positionMs >= (track.durationMs ?? 0) * 0.95);
            });
            void markBookListened(selectedBook, !listened);
          }}
          onRemoveBookDownloads={() => void removeBookDownloads(selectedBook)}
          onMarkTrackListened={(track, listened) => void markTrackListened(selectedBook, track, listened)}
          onRemoveTrackDownload={(track) => void removeTrackDownload(selectedBook, track)}
          onContinue={() => void continueBook()}
          savedTrackId={savedBookProgress?.trackId ?? null}
          savedPositionMs={savedBookProgress?.positionMs ?? 0}
          isBookListened={
            tracks.length > 0 &&
            tracks.every((track) => {
              const saved = progressByBook.get(selectedBook.id);
              if (!saved) return false;
              return (
                track.sortOrder < (tracks.find((item) => item.id === saved.trackId)?.sortOrder ?? Infinity) ||
                (track.id === saved.trackId && saved.positionMs >= (track.durationMs ?? 0) * 0.95)
              );
            })
          }
          hasDownloads={tracks.some((t) => t.localPath)}
          allDownloaded={isBookFullyDownloaded(selectedBook.id, tracksByBookId)}
        />
      ) : selectedCycle ? (
        <CycleDetailPage
          cycle={selectedCycle}
          cardState={cycleCardStateById[selectedCycle.id] ?? computeCycleCardState(selectedCycle, downloadedBookIds, tracksByBookId, progressByBook)}
          downloadedBookIds={downloadedBookIds}
          tracksByBookId={tracksByBookId}
          progressByBook={progressByBook}
          onBack={() => setSelectedCycle(null)}
          onBookClick={(book) => void openBook(book, selectedCycle)}
          onDownloadCycle={() => void downloadCycle(selectedCycle)}
          onToggleCycleListened={() => {
            const state = cycleCardStateById[selectedCycle.id];
            void Promise.all(
              selectedCycle.books.map((b) => markBookListened(b, !state?.isListened)),
            );
          }}
          onRemoveCycleDownloads={() =>
            void Promise.all(selectedCycle.books.map((b) => removeBookDownloads(b)))
          }
        />
      ) : activeTab === "music" || activeTab === "books" ? (
        <div className="library-route">
          <LibraryPage
            cycles={filteredCycles}
            cycleCardStateById={cycleCardStateById}
            musicTracks={visibleMusicTracks}
            query={query}
            section={activeTab}
            offlineBanner={sessionState === "AuthenticatedOffline"}
            isLoading={isLoading}
            downloadQueue={downloadQueue.state}
            activeMusicTrackId={music.musicMode ? (currentTrack?.id ?? null) : null}
            musicWaveTitle={music.musicMode ? miniTitle : null}
            musicWaveSubtitle={music.musicMode ? miniSubtitle : null}
            musicWaveIsPlaying={music.musicMode && isPlaying}
            musicError={music.musicError}
            cyclePlayingId={cyclePlayingId}
            cycleIsPlaying={Boolean(cyclePlayingId && isPlaying && !music.musicMode)}
            onQueryChange={setQuery}
            onCycleClick={setSelectedCycle}
            onCyclePlay={(cycle) => void playCycle(cycle)}
            onFilterClick={() => setShowFilterSheet(true)}
            onMusicWavePlay={music.playMusicWave}
            onMusicTrackClick={(track) => void music.playMusicTrack(track)}
            onMusicTrackDownload={(track) => void music.downloadMusicTrack(track)}
            onMusicTrackDelete={(track) => void music.deleteMusicTrack(track)}
            onDownloadAllMusic={() => void music.downloadAllMusic()}
          />
          <LibraryFilterSheet
            visible={activeTab === "books" && showFilterSheet}
            filter={filter}
            onDismiss={() => setShowFilterSheet(false)}
            onApply={() => setShowFilterSheet(false)}
            onReset={() => setFilter(defaultFilter)}
            onContentFilterChange={(contentFilter) => setFilter((f) => ({ ...f, contentFilter }))}
            onSortOrderChange={(sortOrder) => setFilter((f) => ({ ...f, sortOrder }))}
          />
        </div>
      ) : activeTab === "downloads" ? (
        <DownloadsPage
          downloadQueue={downloadQueue.state}
          completedItems={completedDownloads}
          books={books}
          cycles={cycles}
          onCancelTrack={(bookId, trackId) => void downloadQueue.cancelTrack(bookId, trackId)}
          onCancelAll={() => void downloadQueue.cancelAll()}
          onDeleteCompleted={(bookId, trackId) => {
            void downloadQueue.cancelTrack(bookId, trackId);
            void window.tonezen.download.delete(bookId, trackId).then(() => refreshLibrary());
          }}
        />
      ) : (
        <ProfilePage
          displayName={displayName}
          email={userEmail}
          avatarUrl={avatarUrl}
          memberSinceEpochMs={memberSinceEpochMs}
          online={sessionState === "AuthenticatedOnline"}
          pendingCount={pendingCount}
          lastSyncAtEpochMs={lastSyncAtEpochMs}
          storageUsedBytes={storageUsed}
          showSignOutConfirm={showSignOutConfirm}
          showSyncDialog={showSyncDialog}
          syncing={syncing}
          onRequestSignOut={() => setShowSignOutConfirm(true)}
          onConfirmSignOut={() => {
            setShowSignOutConfirm(false);
            void handleLogout();
          }}
          onCancelSignOut={() => setShowSignOutConfirm(false)}
          onSyncNow={() => {
            if (sessionState === "AuthenticatedOffline") {
              setShowSyncDialog(true);
              return;
            }
            setSyncing(true);
            void window.tonezen.sync
              .trigger()
              .then(() => refreshLibrary())
              .finally(() => setSyncing(false));
          }}
          onCloseSyncDialog={() => setShowSyncDialog(false)}
          onProfileUpdated={() => void refreshSession()}
          onDeleteAllDownloads={() => void deleteAllDownloads()}
        />
      )}
      {error && <p className="error-text">{error}</p>}
    </AppShell>
  );

  return (
    <>
      {shell}
      {toastMessage && <ToastMessage message={toastMessage} />}
      {earlierChapterPrompt && selectedBook && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">Начать с этой главы?</h2>
            <p className="mt-2 text-sm text-muted">
              Вы уже слушали более позднюю главу. Начать выбранную главу с начала?
            </p>
            <div className="mt-4 flex gap-3">
              <button
                type="button"
                className="btn-secondary flex-1"
                onClick={() => setEarlierChapterPrompt(null)}
              >
                Отмена
              </button>
              <button
                type="button"
                className="btn-primary flex-1"
                onClick={() => {
                  const track = earlierChapterPrompt;
                  setEarlierChapterPrompt(null);
                  if (track) void playAudiobookTrackResolved(selectedBook, tracks, track, 0);
                }}
              >
                Начать
              </button>
            </div>
          </div>
        </div>
      )}
      <NowPlayingSheet
        visible={showExpandedPlayer && Boolean(currentTrack)}
        title={miniTitle ?? ""}
        subtitle={miniSubtitle ?? ""}
        coverSeed={coverSeed}
        isPlaying={isPlaying}
        positionMs={positionMs}
        durationMs={durationMs}
        isMusic={music.musicMode}
        waveformPeaks={currentTrack?.waveformPeaks ?? null}
        downloadProgress={miniDownloadProgress}
        controlsDisabled={miniDownloadProgress != null}
        onDismiss={() => setShowExpandedPlayer(false)}
        onPlayPause={() => music.onMiniPlayerPlayPause(activeMusicTrack)}
        onSeekBy={seekBy}
        onSkipPrevious={handleSkipPrevious}
        onSkipNext={handleSkipNext}
        onSeek={seekTo}
        volume={volume}
        onVolumeChange={setVolume}
      />
      <audio ref={audioRef} className="hidden" onEnded={handleTrackEnded} onTimeUpdate={onTimeUpdate} />
    </>
  );
}
