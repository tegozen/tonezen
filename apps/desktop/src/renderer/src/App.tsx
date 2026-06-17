import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Book, Cycle, Track } from "@shared/types";
import {
  buildMusicTrackListForCatalogUpdate,
  visibleMusicTrackList,
  type MusicListTrack,
} from "@shared/musicList";
import { completedDownloadItems } from "@shared/downloadsPageState";
import { progressForTrack } from "@shared/downloadQueueState";
import { CyclePlaybackResolver } from "@shared/cyclePlayback";
import { AppShell } from "./components/AppShell";
import { LibraryFilterSheet } from "./components/LibraryFilterSheet";
import { LoginView } from "./components/LoginView";
import { NowPlayingSheet } from "./components/NowPlayingSheet";
import { useDownloadQueue } from "./hooks/useDownloadQueue";
import { useMusicPlayback } from "./hooks/useMusicPlayback";
import { usePlayback } from "./hooks/usePlayback";
import { useTonezenSession } from "./hooks/useTonezenSession";
import type { BottomTab, LibraryFilter } from "./i18n/strings";
import { strings } from "./i18n/strings";
import { resolveDownloadError } from "./lib/errorMessages";
import {
  buildTracksByBookId,
  computeCycleCardState,
  filterAndSortCycles,
  isBookFullyDownloaded,
} from "./lib/cycleUtils";
import { BookDetailPage } from "./pages/BookDetailPage";
import { CycleDetailPage } from "./pages/CycleDetailPage";
import { DownloadsPage } from "./pages/DownloadsPage";
import { LibraryPage } from "./pages/LibraryPage";
import { ProfilePage } from "./pages/ProfilePage";

const cycleResolver = new CyclePlaybackResolver();
const defaultFilter: LibraryFilter = { contentFilter: "all", sortOrder: "recent" };

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
    setError,
    login,
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
  const [showDownloadSheet, setShowDownloadSheet] = useState(false);
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

  const downloadQueue = useDownloadQueue();
  const musicStartedInSessionRef = useRef(false);
  const refreshLibraryRef = useRef<() => Promise<void>>(async () => {});

  const refreshLibrary = useCallback(async (options?: { rebuildMusic?: boolean }) => {
    const rebuildMusic = options?.rebuildMusic ?? true;
    const [library, stats, sync, progress] = await Promise.all([
      window.tonezen.db.getLibrarySnapshot(),
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
    resumeProgress,
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
      void refreshLibrary();
      return;
    }
    setIsLoading(true);
    void window.tonezen.catalog
      .sync()
      .then(() => refreshLibrary({ rebuildMusic: true }))
      .catch(() => refreshLibrary());
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
    if (sessionState === "AuthenticatedOffline" || sessionState === "Unauthenticated") {
      return null;
    }
    try {
      const book = books.find((item) => item.id === bookId);
      const trackMeta = bookTracks.find((item) => item.id === trackId);
      const result = await downloadQueue.awaitTrack(bookId, trackId, {
        priority: "PLAY",
        title: trackMeta?.title ?? trackId,
        subtitle: book?.title ?? null,
        contentType: book?.contentType ?? "audiobook",
      });
      if (result !== "COMPLETED") return null;
      bookTracks = await window.tonezen.db.getTracks(bookId);
      track = bookTracks.find((item) => item.id === trackId);
      await refreshLibrary();
      return (track as Track) ?? null;
    } catch {
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

  const playBookTrack = async (track: Track) => {
    if (!selectedBook) return;
    music.setMusicMode(false);
    const local = track.localPath ? track : await ensureAudiobookTrackLocal(selectedBook.id, track.id);
    if (local?.localPath) {
      playTrack(local, 0, selectedBook);
      setShowExpandedPlayer(false);
    } else if (!track.localPath) {
      setShowDownloadSheet(true);
    }
  };

  const playCycle = async (cycle: Cycle) => {
    if (cyclePlayingId === cycle.id && isPlaying) {
      pauseOrResume();
      return;
    }
    setCyclePlayingId(cycle.id);
    music.setMusicMode(false);
    for (const book of cycle.books) {
      const bookTracks = await window.tonezen.db.getTracks(book.id);
      const saved = await window.tonezen.progress.get(book.id);
      let startTrack = bookTracks[0];
      if (saved) {
        startTrack = bookTracks.find((item) => item.id === saved.trackId) ?? bookTracks[0];
      }
      if (!startTrack) continue;
      const local = startTrack.localPath ? startTrack : await ensureAudiobookTrackLocal(book.id, startTrack.id);
      if (local?.localPath) {
        setSelectedBook(book as Book);
        setTracks(bookTracks as Track[]);
        playTrack(local, saved?.positionMs ?? 0, book);
        return;
      }
    }
  };

  const downloadBook = async (book: Book) => {
    setShowDownloadSheet(false);
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    const pending = bookTracks.filter((track) => !track.localPath);
    if (pending.length === 0) return;
    const batchId = crypto.randomUUID();
    try {
      await downloadQueue.enqueueBatch(
        pending.map((track) => ({
          bookId: book.id,
          trackId: track.id,
          priority: "BULK" as const,
          batchId,
          title: track.title,
          subtitle: book.title,
          contentType: book.contentType,
        })),
        batchId,
      );
    } catch (e) {
      setError(resolveDownloadError(e instanceof Error ? e.message : ""));
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

  const handleTrackEnded = () => {
    if (music.handleTrackEnded()) return;
    if (!currentTrack || !selectedBook) return;

    const nextInBook = cycleResolver.nextInBook(currentTrack, tracks);
    if (nextInBook) {
      void playBookTrack(nextInBook);
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
      void playBookTrack(result.track as Track);
    })();
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
  const activeMusicTrack = music.musicQueue.find((track) => track.trackId === currentTrack?.id);
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
        strings.nowPlaying
      : strings.nowPlaying
    : selectedBook?.author ?? strings.nowPlaying;
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
          showDownloadSheet={showDownloadSheet}
          onBack={() => {
            setSelectedBook(null);
            setShowDownloadSheet(false);
          }}
          onTrackClick={(track) => void playBookTrack(track)}
          onDownloadRequest={() => setShowDownloadSheet(true)}
          onDownloadConfirm={() => void downloadBook(selectedBook)}
          onDownloadDismiss={() => setShowDownloadSheet(false)}
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
          onContinue={() => void resumeProgress()}
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
            musicError={music.musicError}
            cyclePlayingId={cyclePlayingId}
            cycleIsPlaying={Boolean(cyclePlayingId && isPlaying && !music.musicMode)}
            onQueryChange={setQuery}
            onCycleClick={setSelectedCycle}
            onCyclePlay={(cycle) => void playCycle(cycle)}
            onFilterClick={() => setShowFilterSheet(true)}
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
