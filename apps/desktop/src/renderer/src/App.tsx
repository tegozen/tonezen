import { useCallback, useEffect, useMemo, useState } from "react";
import type { Book, Track } from "@shared/types";
import { AppShell } from "./components/AppShell";
import { LoginView } from "./components/LoginView";
import { usePlayback } from "./hooks/usePlayback";
import { useTonezenSession } from "./hooks/useTonezenSession";
import type { BottomTab } from "./i18n/strings";
import { strings } from "./i18n/strings";
import { BookFlowPage } from "./pages/BookFlowPage";
import { DownloadsPage } from "./pages/DownloadsPage";
import { LibraryPage } from "./pages/LibraryPage";
import { PlayerPage } from "./pages/PlayerPage";
import { ProfilePage } from "./pages/ProfilePage";

export function App() {
  const {
    sessionState,
    email,
    setEmail,
    password,
    setPassword,
    error,
    setError,
    login,
    logout,
  } = useTonezenSession();

  const [activeTab, setActiveTab] = useState<BottomTab>("library");
  const [books, setBooks] = useState<Book[]>([]);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [activeBook, setActiveBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [bookTab, setBookTab] = useState<"player" | "details">("player");
  const [libraryTab, setLibraryTab] = useState(0);
  const [query, setQuery] = useState("");
  const [showFilterSheet, setShowFilterSheet] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<string[]>([]);
  const [downloads, setDownloads] = useState<
    Awaited<ReturnType<typeof window.tonezen.download.list>>
  >([]);
  const [storageUsed, setStorageUsed] = useState(0);
  const [downloadsTab, setDownloadsTab] = useState(0);
  const [showDeleteAllConfirm, setShowDeleteAllConfirm] = useState(false);
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showSignOutConfirm, setShowSignOutConfirm] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [pendingCount, setPendingCount] = useState(0);
  const [showDownloadSheet, setShowDownloadSheet] = useState(false);
  const [syncingCatalog, setSyncingCatalog] = useState(false);

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
    resumeProgress,
    setInitialTrackState,
    pauseOrResume,
    seekBy,
  } = usePlayback(selectedBook, tracks);

  const downloadedIds = useMemo(
    () =>
      new Set(
        books.filter((book) =>
          tracks.some((track) => track.bookId === book.id && track.localPath),
        ).map((book) => book.id),
      ),
    [books, tracks],
  );

  const refreshBooks = useCallback(async () => {
    setBooks(await window.tonezen.db.getBooks());
    setFavoriteIds(await window.tonezen.favorites.list());
    setDownloads(await window.tonezen.download.list());
    const stats = await window.tonezen.download.storageStats();
    setStorageUsed(stats.usedBytes);
    const sync = await window.tonezen.sync.status();
    setPendingCount(sync.pendingCount);
  }, []);

  useEffect(() => {
    if (sessionState !== "Unauthenticated") {
      void refreshBooks();
    }
  }, [sessionState, refreshBooks]);

  const syncCatalog = async () => {
    setSyncingCatalog(true);
    try {
      const synced = await window.tonezen.catalog.sync();
      setBooks(synced as Book[]);
    } finally {
      setSyncingCatalog(false);
    }
  };

  const handleLogin = async () => {
    const ok = await login();
    if (ok) {
      await syncCatalog();
      await refreshBooks();
    }
  };

  const handleLogout = async () => {
    stopPlayback();
    setSelectedBook(null);
    await logout();
  };

  const openBook = async (book: Book) => {
    setSelectedBook(book);
    setActiveBook(book);
    setBookTab("player");
    const bookTracks = await window.tonezen.db.getTracks(book.id);
    setTracks(bookTracks as Track[]);
    const saved = await window.tonezen.progress.get(book.id);
    setInitialTrackState(book, bookTracks as Track[], saved);
  };

  const downloadBook = async () => {
    if (!selectedBook) return;
    setShowDownloadSheet(false);
    try {
      for (const track of tracks) {
        if (!track.localPath) {
          await window.tonezen.download.track(selectedBook.id, track.id);
        }
      }
      await openBook(selectedBook);
      await refreshBooks();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    }
  };

  const toggleFavorite = async () => {
    if (!selectedBook) return;
    setFavoriteIds(await window.tonezen.favorites.toggle(selectedBook.id));
  };

  const upNext = useMemo(() => {
    if (!currentTrack) return [];
    const index = tracks.findIndex((track) => track.id === currentTrack.id);
    return tracks.slice(index + 1);
  }, [currentTrack, tracks]);

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

  if (selectedBook) {
    return (
      <>
        <AppShell
          activeTab={activeTab}
          onTabSelect={setActiveTab}
          miniTitle={currentTrack?.title ?? selectedBook.title}
          miniSubtitle={selectedBook.author ?? strings.nowPlaying}
          isPlaying={isPlaying}
          onMiniBarClick={() => setActiveTab("player")}
          onMiniPlayPause={pauseOrResume}
        >
          <BookFlowPage
            book={selectedBook}
            tracks={tracks}
            tab={bookTab}
            isPlaying={isPlaying}
            isFavorite={favoriteIds.includes(selectedBook.id)}
            positionMs={positionMs}
            durationMs={durationMs}
            currentTrack={currentTrack}
            showDownloadSheet={showDownloadSheet}
            onBack={() => setSelectedBook(null)}
            onTabChange={setBookTab}
            onPlayPause={pauseOrResume}
            onSeekBy={seekBy}
            onTrackClick={(track) => track.localPath && playTrack(track)}
            onDownloadRequest={() => setShowDownloadSheet(true)}
            onDownloadConfirm={() => void downloadBook()}
            onDownloadDismiss={() => setShowDownloadSheet(false)}
            onToggleFavorite={() => void toggleFavorite()}
            onStartListening={() => void resumeProgress()}
          />
        </AppShell>
        <audio ref={audioRef} className="hidden" onEnded={onTrackEnded} onTimeUpdate={onTimeUpdate} />
      </>
    );
  }

  return (
    <>
      <AppShell
        activeTab={activeTab}
        onTabSelect={setActiveTab}
        miniTitle={currentTrack?.title ?? activeBook?.title ?? null}
        miniSubtitle={activeBook?.author ?? strings.nowPlaying}
        isPlaying={isPlaying}
        onMiniBarClick={() => setActiveTab("player")}
        onMiniPlayPause={pauseOrResume}
      >
        {activeTab === "library" && (
          <>
            <LibraryPage
              books={books}
              downloadedIds={
                new Set(downloads.map((item) => item.bookId))
              }
              query={query}
              selectedTab={libraryTab}
              offlineBanner={sessionState === "AuthenticatedOffline"}
              onQueryChange={setQuery}
              onTabChange={setLibraryTab}
              onBookClick={(book) => void openBook(book)}
              onFilterClick={() => setShowFilterSheet(true)}
            />
            <div className="mt-4">
              <button className="btn-primary" disabled={syncingCatalog} onClick={() => void syncCatalog()}>
                {syncingCatalog ? "Syncing…" : "Sync catalog"}
              </button>
            </div>
            {showFilterSheet && (
              <div className="sheet-overlay" onClick={() => setShowFilterSheet(false)}>
                <div className="sheet-panel" onClick={(e) => e.stopPropagation()}>
                  <h3 className="font-semibold">{strings.searchFilterTitle}</h3>
                  <div className="mt-4 flex gap-2">
                    <button type="button" className="btn-secondary flex-1" onClick={() => setQuery("")}>
                      {strings.reset}
                    </button>
                    <button type="button" className="btn-primary flex-1" onClick={() => setShowFilterSheet(false)}>
                      {strings.apply}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </>
        )}
        {activeTab === "player" && (
          <PlayerPage
            currentTrack={currentTrack}
            book={activeBook}
            isPlaying={isPlaying}
            positionMs={positionMs}
            durationMs={durationMs}
            upNext={upNext}
            favoritesCount={favoriteIds.length}
            downloadsCount={downloads.length}
            onPlayPause={pauseOrResume}
            onSeekBy={seekBy}
            onGoToLibrary={() => setActiveTab("library")}
          />
        )}
        {activeTab === "downloads" && (
          <DownloadsPage
            summaries={downloads}
            usedBytes={storageUsed}
            selectedTab={downloadsTab}
            showDeleteConfirm={showDeleteAllConfirm}
            onTabChange={setDownloadsTab}
            onShowDeleteConfirm={setShowDeleteAllConfirm}
            onDeleteAll={() => {
              void window.tonezen.download.deleteAll().then(refreshBooks);
              setShowDeleteAllConfirm(false);
            }}
          />
        )}
        {activeTab === "profile" && (
          <ProfilePage
            userId={email || "user"}
            online={sessionState === "AuthenticatedOnline"}
            pendingCount={pendingCount}
            storageUsedBytes={storageUsed}
            showMenu={showProfileMenu}
            showSignOutConfirm={showSignOutConfirm}
            showSyncDialog={showSyncDialog}
            syncing={syncing}
            onToggleMenu={() => setShowProfileMenu((value) => !value)}
            onCloseMenu={() => setShowProfileMenu(false)}
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
                .then(refreshBooks)
                .finally(() => setSyncing(false));
            }}
            onCloseSyncDialog={() => setShowSyncDialog(false)}
          />
        )}
        {error && <p className="error-text">{error}</p>}
      </AppShell>
      <audio ref={audioRef} className="hidden" onEnded={onTrackEnded} onTimeUpdate={onTimeUpdate} />
    </>
  );
}
