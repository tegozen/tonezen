import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Book, Cycle, Track } from "@shared/types";
import {
  buildMusicTrackList,
  musicQueueFrom,
  nextMusicIndex,
  previousMusicIndex,
  shuffleMusicTracks,
  type MusicListTrack,
} from "@shared/musicList";
import { CyclePlaybackResolver } from "@shared/cyclePlayback";
import { AppShell } from "./components/AppShell";
import { LibraryFilterSheet } from "./components/LibraryFilterSheet";
import { LoginView } from "./components/LoginView";
import { NowPlayingSheet } from "./components/NowPlayingSheet";
import { usePlayback } from "./hooks/usePlayback";
import { useTonezenSession } from "./hooks/useTonezenSession";
import type { BottomTab, LibraryFilter } from "./i18n/strings";
import { strings } from "./i18n/strings";
import {
  computeCycleCardState,
  filterAndSortCycles,
  isBookFullyDownloaded,
} from "./lib/cycleUtils";
import { BookDetailPage } from "./pages/BookDetailPage";
import { CycleDetailPage } from "./pages/CycleDetailPage";
import { LibraryPage } from "./pages/LibraryPage";
import { ProfilePage } from "./pages/ProfilePage";

const cycleResolver = new CyclePlaybackResolver();
const defaultFilter: LibraryFilter = { contentFilter: "all", sortOrder: "recent" };

export function App() {
  const session = useTonezenSession();
  const {
    sessionState,
    userEmail,
    displayName,
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

  const [activeTab, setActiveTab] = useState<BottomTab>("library");
  const [cycles, setCycles] = useState<Cycle[]>([]);
  const [books, setBooks] = useState<Book[]>([]);
  const [allTracks, setAllTracks] = useState<Track[]>([]);
  const [selectedCycle, setSelectedCycle] = useState<Cycle | null>(null);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [libraryTab, setLibraryTab] = useState(0);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<LibraryFilter>(defaultFilter);
  const [showFilterSheet, setShowFilterSheet] = useState(false);
  const [showExpandedPlayer, setShowExpandedPlayer] = useState(false);
  const [showDownloadSheet, setShowDownloadSheet] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [storageUsed, setStorageUsed] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [showSignOutConfirm, setShowSignOutConfirm] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [musicTracks, setMusicTracks] = useState<MusicListTrack[]>([]);
  const [musicQueue, setMusicQueue] = useState<MusicListTrack[]>([]);
  const [musicMode, setMusicMode] = useState(false);
  const [musicError, setMusicError] = useState<string | null>(null);
  const [musicDownloadingTrackId, setMusicDownloadingTrackId] = useState<string | null>(null);
  const [musicDownloadProgress, setMusicDownloadProgress] = useState<{ done: number; total: number } | null>(null);
  const [cyclePlayingId, setCyclePlayingId] = useState<string | null>(null);
  const [progressList, setProgressList] = useState<Array<{ bookId: string; trackId: string; positionMs: number; updatedAt: string }>>([]);
  const musicShuffledRef = useRef(false);

  const playbackBook = useMemo(() => {
    if (musicMode && tracks.length > 0) {
      return books.find((b) => b.id === tracks[0].bookId) ?? selectedBook;
    }
    return selectedBook;
  }, [musicMode, tracks, books, selectedBook]);

  const skipTracks = useMemo(() => {
    if (!musicMode) return tracks;
    return musicQueue
      .map((mt) => allTracks.find((t) => t.id === mt.trackId))
      .filter((t): t is Track => t != null);
  }, [musicMode, musicQueue, allTracks, tracks]);

  const {
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    onTrackEnded,
    setInitialTrackState,
    pauseOrResume,
    seekBy,
    seekTo,
  } = usePlayback(playbackBook, tracks, skipTracks);

  const downloadedBookIds = useMemo(() => {
    const ids = new Set<string>();
    for (const book of books) {
      if (isBookFullyDownloaded(book.id, allTracks)) ids.add(book.id);
    }
    return ids;
  }, [books, allTracks]);

  const progressByBook = useMemo(
    () => new Map(progressList.map((p) => [p.bookId, p])),
    [progressList],
  );

  const cycleCardStateById = useMemo(() => {
    const map: Record<string, ReturnType<typeof computeCycleCardState>> = {};
    for (const cycle of cycles) {
      map[cycle.id] = computeCycleCardState(cycle, downloadedBookIds, allTracks, progressByBook);
    }
    return map;
  }, [cycles, downloadedBookIds, allTracks, progressByBook]);

  const filteredCycles = useMemo(
    () => filterAndSortCycles(cycles, query, filter, downloadedBookIds, progressByBook),
    [cycles, query, filter, downloadedBookIds, progressByBook],
  );

  const refreshLibrary = useCallback(async () => {
    const [loadedCycles, loadedBooks, loadedTracks, downloads, stats, sync, progress] = await Promise.all([
      window.tonezen.db.getCycles(),
      window.tonezen.db.getBooks(),
      window.tonezen.db.getAllTracks(),
      window.tonezen.download.list(),
      window.tonezen.download.storageStats(),
      window.tonezen.sync.status(),
      window.tonezen.db.getAllProgress(),
    ]);
    setCycles(loadedCycles as Cycle[]);
    setBooks(loadedBooks as Book[]);
    setAllTracks(loadedTracks as Track[]);
    setStorageUsed(stats.usedBytes);
    setPendingCount(sync.pendingCount);
    setProgressList(progress);
    setMusicTracks(buildMusicTrackList(loadedBooks as Book[], loadedTracks as Track[]));
    void downloads;
    setIsLoading(false);
  }, []);

  useEffect(() => {
    if (sessionState !== "Unauthenticated") {
      void refreshLibrary();
    }
  }, [sessionState, refreshLibrary]);

  useEffect(() => {
    if (libraryTab === 1 && !musicShuffledRef.current) {
      musicShuffledRef.current = true;
      setMusicTracks((prev) => (prev.length > 0 ? shuffleMusicTracks(prev) : prev));
    }
  }, [libraryTab]);

  const syncCatalog = async () => {
    setIsLoading(true);
    try {
      await window.tonezen.catalog.sync();
      await refreshLibrary();
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogin = async () => {
    const ok = await login();
    if (ok) {
      await syncCatalog();
      await refreshLibrary();
    }
  };

  const handleLogout = async () => {
    stopPlayback();
    setSelectedBook(null);
    setSelectedCycle(null);
    setMusicMode(false);
    await logout();
  };

  const ensureTrackLocal = async (bookId: string, trackId: string): Promise<Track | null> => {
    let bookTracks = await window.tonezen.db.getTracks(bookId);
    let track = bookTracks.find((t) => t.id === trackId);
    if (track?.localPath) return track as Track;
    if (sessionState === "AuthenticatedOffline") {
      setMusicError(strings.musicPlaybackErrorOffline);
      return null;
    }
    if (sessionState === "Unauthenticated") {
      setMusicError(strings.musicPlaybackErrorLogin);
      return null;
    }
    try {
      await window.tonezen.download.track(bookId, trackId);
      bookTracks = await window.tonezen.db.getTracks(bookId);
      track = bookTracks.find((t) => t.id === trackId);
      await refreshLibrary();
      return (track as Track) ?? null;
    } catch {
      setMusicError(strings.musicPlaybackErrorDownload);
      return null;
    }
  };

  const openBook = async (book: Book, fromCycle: Cycle | null = selectedCycle) => {
    setSelectedBook(book);
    setMusicMode(false);
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    setTracks(bookTracks as Track[]);
    const saved = await window.tonezen.progress.get(book.id);
    setInitialTrackState(book, bookTracks as Track[], saved);
    if (fromCycle) setSelectedCycle(fromCycle);
  };

  const playBookTrack = async (track: Track) => {
    if (!selectedBook) return;
    setMusicMode(false);
    setMusicError(null);
    const local = track.localPath ? track : await ensureTrackLocal(selectedBook.id, track.id);
    if (local?.localPath) {
      playTrack(local);
      setShowExpandedPlayer(false);
    } else if (!track.localPath) {
      setShowDownloadSheet(true);
    }
  };

  const playMusicTrack = async (listTrack: MusicListTrack) => {
    if (musicDownloadingTrackId) return;
    const book = books.find((b) => b.id === listTrack.bookId);
    if (!book) return;

    if (musicMode && currentTrack?.id === listTrack.trackId) {
      pauseOrResume();
      return;
    }

    setMusicError(null);
    setMusicMode(true);
    setMusicDownloadingTrackId(listTrack.trackId);
    const local = listTrack.isDownloaded
      ? allTracks.find((t) => t.id === listTrack.trackId)
      : await ensureTrackLocal(listTrack.bookId, listTrack.trackId);
    setMusicDownloadingTrackId(null);
    if (!local?.localPath) return;

    const queue = musicQueueFrom(musicTracks, listTrack.trackId);
    setMusicQueue(queue);
    const bookTracks = await window.tonezen.db.getTracks(listTrack.bookId);
    setTracks(bookTracks as Track[]);
    playTrack(local);
  };

  const playCycle = async (cycle: Cycle) => {
    if (cyclePlayingId === cycle.id && isPlaying) {
      pauseOrResume();
      return;
    }
    setCyclePlayingId(cycle.id);
    setMusicMode(false);
    for (const book of cycle.books) {
      const bookTracks = await window.tonezen.db.getTracks(book.id);
      const saved = await window.tonezen.progress.get(book.id);
      let startTrack = bookTracks[0];
      if (saved) {
        startTrack = bookTracks.find((t) => t.id === saved.trackId) ?? bookTracks[0];
      }
      if (!startTrack) continue;
      const local = startTrack.localPath ? startTrack : await ensureTrackLocal(book.id, startTrack.id);
      if (local?.localPath) {
        setSelectedBook(book as Book);
        setTracks(bookTracks as Track[]);
        playTrack(local, saved?.positionMs ?? 0);
        return;
      }
    }
  };

  const downloadBook = async (book: Book) => {
    setShowDownloadSheet(false);
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    try {
      for (const track of bookTracks) {
        if (!track.localPath) {
          await window.tonezen.download.track(book.id, track.id);
        }
      }
      await refreshLibrary();
      if (selectedBook?.id === book.id) {
        const updated = await window.tonezen.db.getTracks(book.id);
        setTracks(updated as Track[]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : strings.downloadFailed);
    }
  };

  const downloadCycle = async (cycle: Cycle) => {
    for (const book of cycle.books) {
      await downloadBook(book);
    }
  };

  const removeBookDownloads = async (book: Book) => {
    const bookTracks = await window.tonezen.db.getTracks(book.id);
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
    if (musicMode && musicQueue.length > 0 && currentTrack) {
      const idx = musicQueue.findIndex((t) => t.trackId === currentTrack.id);
      const nextIdx = nextMusicIndex(idx, musicQueue.length);
      void playMusicTrack(musicQueue[nextIdx]);
      return;
    }
    if (!currentTrack || !selectedBook) return;
    const next = cycleResolver.nextInBook(currentTrack, tracks);
    if (next) void playBookTrack(next);
  };

  const handleSkipPrevious = () => {
    if (musicMode && musicQueue.length > 0 && currentTrack) {
      const idx = musicQueue.findIndex((t) => t.trackId === currentTrack.id);
      const prevIdx = previousMusicIndex(idx, musicQueue.length);
      void playMusicTrack(musicQueue[prevIdx]);
      return;
    }
    if (!currentTrack || !selectedBook) return;
    const prev = cycleResolver.previousInBook(currentTrack, tracks);
    if (prev) void playBookTrack(prev);
  };

  const handleTrackEnded = () => {
    if (musicMode && musicQueue.length > 0 && currentTrack) {
      const idx = musicQueue.findIndex((t) => t.trackId === currentTrack.id);
      const nextIdx = nextMusicIndex(idx, musicQueue.length);
      void playMusicTrack(musicQueue[nextIdx]);
      return;
    }
    onTrackEnded();
  };

  const downloadAllMusic = async () => {
    const pending = musicTracks.filter((t) => !t.isDownloaded);
    setMusicDownloadProgress({ done: 0, total: pending.length });
    let done = 0;
    for (const track of pending) {
      await ensureTrackLocal(track.bookId, track.trackId);
      done += 1;
      setMusicDownloadProgress({ done, total: pending.length });
    }
    setMusicDownloadProgress(null);
    await refreshLibrary();
  };

  const trackProgress = useMemo(() => {
    const map = new Map<string, number>();
    if (!selectedBook) return map;
    const saved = progressByBook.get(selectedBook.id);
    if (!saved) return map;
    for (const track of tracks) {
      if (track.id === saved.trackId && track.durationMs) {
        map.set(track.id, saved.positionMs / track.durationMs);
      } else if (track.sortOrder < (tracks.find((t) => t.id === saved.trackId)?.sortOrder ?? Infinity)) {
        map.set(track.id, 1);
      }
    }
    return map;
  }, [selectedBook, tracks, progressByBook]);

  const miniTitle = currentTrack?.title ?? null;
  const miniSubtitle = musicMode
    ? musicQueue.find((t) => t.trackId === currentTrack?.id)?.artist ?? strings.nowPlaying
    : selectedBook?.author ?? playbackBook?.author ?? strings.nowPlaying;
  const showMiniPlayer = Boolean(currentTrack);
  const showBottomNav = !selectedBook && !selectedCycle;
  const coverSeed = currentTrack?.id ?? selectedBook?.id ?? "";

  if (sessionState === "Unauthenticated") {
    return (
      <LoginView
        email={email}
        password={password}
        error={error}
        onEmailChange={setEmail}
        onPasswordChange={setPassword}
        onLogin={() => void handleLogin()}
      />
    );
  }

  const shell = (
    <AppShell
      activeTab={activeTab}
      onTabSelect={setActiveTab}
      miniTitle={miniTitle}
      miniSubtitle={miniSubtitle}
      isPlaying={isPlaying}
      positionMs={positionMs}
      durationMs={durationMs}
      showMiniPlayer={showMiniPlayer}
      showBottomNav={showBottomNav}
      onMiniBarClick={() => setShowExpandedPlayer(true)}
      onMiniPlayPause={() => {
        if (musicMode && currentTrack) {
          pauseOrResume();
        } else if (cyclePlayingId) {
          pauseOrResume();
        } else {
          pauseOrResume();
        }
      }}
    >
      {selectedBook ? (
        <BookDetailPage
          book={selectedBook}
          tracks={tracks}
          currentTrackId={currentTrack?.id ?? null}
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
            const listened = trackProgress.size > 0 && [...trackProgress.values()].every((v) => v >= 0.95);
            void markBookListened(selectedBook, !listened);
          }}
          onRemoveBookDownloads={() => void removeBookDownloads(selectedBook)}
          trackProgress={trackProgress}
          isBookListened={[...trackProgress.values()].every((v) => v >= 0.95) && trackProgress.size > 0}
          hasDownloads={tracks.some((t) => t.localPath)}
          allDownloaded={isBookFullyDownloaded(selectedBook.id, allTracks)}
        />
      ) : selectedCycle ? (
        <CycleDetailPage
          cycle={selectedCycle}
          cardState={cycleCardStateById[selectedCycle.id] ?? computeCycleCardState(selectedCycle, downloadedBookIds, allTracks, progressByBook)}
          downloadedBookIds={downloadedBookIds}
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
      ) : activeTab === "library" ? (
        <>
          <LibraryPage
            cycles={filteredCycles}
            cycleCardStateById={cycleCardStateById}
            musicTracks={musicTracks}
            query={query}
            selectedTab={libraryTab}
            offlineBanner={sessionState === "AuthenticatedOffline"}
            isLoading={isLoading}
            musicDownloadProgress={musicDownloadProgress}
            musicDownloadingTrackId={musicDownloadingTrackId}
            activeMusicTrackId={musicMode ? (currentTrack?.id ?? null) : null}
            isMusicPlaying={musicMode && isPlaying}
            musicError={musicError}
            cyclePlayingId={cyclePlayingId}
            cycleIsPlaying={Boolean(cyclePlayingId && isPlaying && !musicMode)}
            onQueryChange={setQuery}
            onTabChange={setLibraryTab}
            onCycleClick={setSelectedCycle}
            onCyclePlay={(cycle) => void playCycle(cycle)}
            onFilterClick={() => setShowFilterSheet(true)}
            onMusicTrackClick={(track) => void playMusicTrack(track)}
            onMusicTrackDelete={(track) =>
              void window.tonezen.download.delete(track.bookId, track.trackId).then(refreshLibrary)
            }
            onDownloadAllMusic={() => void downloadAllMusic()}
          />
          <LibraryFilterSheet
            visible={showFilterSheet}
            filter={filter}
            onDismiss={() => setShowFilterSheet(false)}
            onApply={() => setShowFilterSheet(false)}
            onReset={() => setFilter(defaultFilter)}
            onContentFilterChange={(contentFilter) => setFilter((f) => ({ ...f, contentFilter }))}
            onSortOrderChange={(sortOrder) => setFilter((f) => ({ ...f, sortOrder }))}
          />
        </>
      ) : (
        <ProfilePage
          displayName={displayName}
          email={userEmail}
          online={sessionState === "AuthenticatedOnline"}
          pendingCount={pendingCount}
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
              .then(refreshLibrary)
              .finally(() => setSyncing(false));
          }}
          onCloseSyncDialog={() => setShowSyncDialog(false)}
          onProfileUpdated={() => void refreshSession()}
          onDeleteAllDownloads={() => void window.tonezen.download.deleteAll().then(refreshLibrary)}
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
        isMusic={musicMode}
        onDismiss={() => setShowExpandedPlayer(false)}
        onPlayPause={pauseOrResume}
        onSeekBy={seekBy}
        onSkipPrevious={handleSkipPrevious}
        onSkipNext={handleSkipNext}
        onSeek={seekTo}
      />
      <audio ref={audioRef} className="hidden" onEnded={handleTrackEnded} onTimeUpdate={onTimeUpdate} />
    </>
  );
}
