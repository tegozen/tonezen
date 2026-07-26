import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Book, Cycle } from "@core/types";
import { progressForTrack } from "@core/downloads/downloadQueueState";
import { findActiveMusicTrack } from "@core/playback/musicPlayback";
import { AppShell, BottomNav } from "@/widgets/app-shell";
import { MiniPlayerBar } from "@/widgets/mini-player";
import { LibraryFilterSheet } from "@/features/library-filter";
import { LoginView } from "@/pages/login";
import { NowPlayingSheet } from "@/widgets/now-playing";
import { ToastMessage } from "@/shared/ui/ToastMessage";
import { useToast } from "@/shared/lib/useToast";
import { getTonezenApi, useDeleteDownloadMutation, useTriggerSyncMutation } from "@/shared/api";
import { useDownloadQueue, useLibraryDownloads } from "@/features/downloads";
import { useMusicPlayback } from "@/features/music-queue";
import { EarlierChapterPrompt, useAudiobookSession, usePlayback } from "@/features/playback";
import { useTonezenSession } from "@/features/auth";
import { useLibraryController } from "@/features/library";
import { useIpcQueryInvalidation } from "@/app/useIpcQueryInvalidation";
import type { BottomTab } from "@core/platform/navigation";
import { computeCycleCardState, isBookFullyDownloaded, isBookFullyListened } from "@/entities/catalog";
import { BookDetailPage } from "@/pages/book-detail";
import { CycleDetailPage } from "@/pages/cycle-detail";
import { DownloadsPage } from "@/pages/downloads";
import { LibraryPage } from "@/pages/library";
import { ProfilePage } from "@/pages/profile";

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
  const [showExpandedPlayer, setShowExpandedPlayer] = useState(false);
  const [showSignOutConfirm, setShowSignOutConfirm] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const { toastMessage, showToast } = useToast();
  const downloadQueue = useDownloadQueue();
  const authenticated = sessionState !== "Unauthenticated";
  useIpcQueryInvalidation(authenticated);
  const library = useLibraryController({ sessionState, downloadQueueState: downloadQueue.state });

  const closeExpandedPlayer = useCallback(() => setShowExpandedPlayer(false), []);

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
  } = usePlayback(library.selectedBook, library.tracks, library.tracks, skipHandlers);

  const music = useMusicPlayback({
    books: library.books,
    allTracks: library.allTracks,
    musicTracks: library.musicTracks,
    setMusicTracks: library.setMusicTracks,
    setTracks: library.setTracks,
    sessionState,
    refreshLibrary: library.refreshLibrary,
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
  library.musicStartedInSessionRef.current = music.musicStartedInSessionRef.current;

  const downloads = useLibraryDownloads({
    sessionState,
    downloadQueue,
    currentTrack,
    selectedBook: library.selectedBook,
    setTracks: library.setTracks,
    stopPlayback,
    closeExpandedPlayer,
    refreshLibrary: library.refreshLibrary,
    showToast,
  });

  const audiobook = useAudiobookSession({
    sessionState,
    books: library.books,
    cycles: library.cycles,
    selectedBook: library.selectedBook,
    setSelectedBook: library.setSelectedBook,
    selectedCycle: library.selectedCycle,
    setSelectedCycle: library.setSelectedCycle,
    tracks: library.tracks,
    setTracks: library.setTracks,
    tracksByBookId: library.tracksByBookId,
    progressByBook: library.progressByBook,
    setProgressList: library.setProgressList,
    refreshLibrary: library.refreshLibrary,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    currentTrack,
    durationMs,
    isPlaying,
    showToast,
    closeExpandedPlayer,
    music: {
      setMusicMode: music.setMusicMode,
      handleSkipNext: music.handleSkipNext,
      handleSkipPrevious: music.handleSkipPrevious,
      handleTrackEnded: music.handleTrackEnded,
    },
  });

  const openBook = useCallback(
    (book: Book, fromCycle: Cycle | null = library.selectedCycle) => {
      music.setMusicMode(false);
      return library.openBook(book, fromCycle);
    },
    [library, music],
  );

  const deleteDownload = useDeleteDownloadMutation();
  const triggerSync = useTriggerSyncMutation();

  const syncCatalog = async () => {
    try {
      await getTonezenApi().catalog.sync();
      await library.refreshLibrary({ rebuildMusic: true });
    } catch {
      await library.refreshLibrary({ rebuildMusic: true });
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
    library.setSelectedBook(null);
    library.setSelectedCycle(null);
    music.resetMusicSession();
    library.musicStartedInSessionRef.current = false;
    await logout();
  };

  const savedBookProgress = library.selectedBook
    ? library.progressByBook.get(library.selectedBook.id)
    : undefined;

  const miniTitle = currentTrack?.title ?? null;
  const activeMusicTrack = findActiveMusicTrack(
    music.musicQueue,
    music.musicQueueRef.current,
    currentTrack?.id,
  );
  const miniSubtitle = music.musicMode
    ? activeMusicTrack
      ? [activeMusicTrack.artist, activeMusicTrack.albumTitle].filter(Boolean).join(" · ") ||
        "Сейчас играет"
      : "Сейчас играет"
    : library.selectedBook?.author ?? "Сейчас играет";
  const miniDownloadProgress = currentTrack
    ? progressForTrack(downloadQueue.state, currentTrack.id)
    : null;
  const currentTrackInSelectedBook =
    library.selectedBook != null &&
    currentTrack != null &&
    library.tracks.some((track) => track.id === currentTrack.id);
  const showMiniPlayer =
    Boolean(currentTrack) &&
    (music.musicMode || currentTrackInSelectedBook || (!library.selectedBook && !library.selectedCycle));
  const handleMusicTabSelected = music.onMusicTabSelected;
  const handleTabSelect = useCallback(
    (tab: BottomTab) => {
      setActiveTab(tab);
      if (tab !== "books") library.setShowFilterSheet(false);
      if (tab === "music") handleMusicTabSelected();
      if (tab === "profile") void refreshSession();
    },
    [handleMusicTabSelected, library, refreshSession],
  );

  useEffect(() => {
    if (activeTab === "music") {
      handleMusicTabSelected();
    }
  }, [activeTab, handleMusicTabSelected]);

  const showBottomNav = !library.selectedBook && !library.selectedCycle;
  const coverSeed = currentTrack?.id ?? library.selectedBook?.id ?? "";

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
        <audio ref={audioRef} className="hidden" onEnded={audiobook.handleTrackEnded} onTimeUpdate={onTimeUpdate} />
      </>
    );
  }

  const selectedBook = library.selectedBook;
  const selectedCycle = library.selectedCycle;
  const bookIsListened = isBookFullyListened(library.tracks, savedBookProgress);

  const progress = durationMs > 0 ? positionMs / durationMs : 0;
  const shell = (
    <AppShell
      showMiniPlayer={showMiniPlayer}
      showBottomNav={showBottomNav}
      miniPlayer={
        <MiniPlayerBar
          title={miniTitle}
          subtitle={miniSubtitle}
          coverSeed={coverSeed}
          isPlaying={isPlaying}
          progress={progress}
          downloadProgress={miniDownloadProgress}
          onBarClick={() => setShowExpandedPlayer(true)}
          onPlayPause={() => {
            music.onMiniPlayerPlayPause(activeMusicTrack);
          }}
        />
      }
      bottomNav={<BottomNav active={activeTab} onSelect={handleTabSelect} />}
    >
      {selectedBook ? (
        <BookDetailPage
          book={selectedBook}
          tracks={library.tracks}
          currentTrackId={
            !music.musicMode && currentTrack && library.tracks.some((track) => track.id === currentTrack.id)
              ? currentTrack.id
              : null
          }
          playbackPositionMs={positionMs}
          downloadQueue={downloadQueue.state}
          onBack={() => library.setSelectedBook(null)}
          onTrackClick={(track) => void audiobook.playBookTrack(track)}
          onDownloadRequest={() => void downloads.downloadAllBookTracks(selectedBook)}
          onDownloadTrack={(track) => void downloads.downloadBookTrack(selectedBook, track)}
          onToggleBookListened={() => void audiobook.markBookListened(selectedBook, !bookIsListened)}
          onRemoveBookDownloads={() => void downloads.removeBookDownloads(selectedBook)}
          onMarkTrackListened={(track, listened) => void audiobook.markTrackListened(selectedBook, track, listened)}
          onRemoveTrackDownload={(track) => void downloads.removeTrackDownload(selectedBook, track)}
          onContinue={() => void audiobook.continueBook()}
          savedTrackId={savedBookProgress?.trackId ?? null}
          savedPositionMs={savedBookProgress?.positionMs ?? 0}
          isBookListened={bookIsListened}
          hasDownloads={library.tracks.some((t) => t.localPath)}
          allDownloaded={isBookFullyDownloaded(selectedBook.id, library.tracksByBookId)}
        />
      ) : selectedCycle ? (
        <CycleDetailPage
          cycle={selectedCycle}
          cardState={
            library.cycleCardStateById[selectedCycle.id] ??
            computeCycleCardState(
              selectedCycle,
              library.downloadedBookIds,
              library.tracksByBookId,
              library.progressByBook,
            )
          }
          downloadedBookIds={library.downloadedBookIds}
          tracksByBookId={library.tracksByBookId}
          progressByBook={library.progressByBook}
          onBack={() => library.setSelectedCycle(null)}
          onBookClick={(book) => void openBook(book, selectedCycle)}
          onDownloadCycle={() => void downloads.downloadCycle(selectedCycle)}
          onToggleCycleListened={() => {
            const state = library.cycleCardStateById[selectedCycle.id];
            void Promise.all(
              selectedCycle.books.map((b) => audiobook.markBookListened(b, !state?.isListened)),
            );
          }}
          onRemoveCycleDownloads={() =>
            void Promise.all(selectedCycle.books.map((b) => downloads.removeBookDownloads(b)))
          }
        />
      ) : activeTab === "music" || activeTab === "books" ? (
        <div className="library-route">
          <LibraryPage
            cycles={library.filteredCycles}
            cycleCardStateById={library.cycleCardStateById}
            musicTracks={library.visibleMusicTracks}
            query={library.query}
            section={activeTab}
            offlineBanner={sessionState === "AuthenticatedOffline"}
            isLoading={library.isLoading}
            downloadQueue={downloadQueue.state}
            activeMusicTrackId={music.musicMode ? (currentTrack?.id ?? null) : null}
            musicWaveTitle={music.musicMode ? miniTitle : null}
            musicWaveSubtitle={music.musicMode ? miniSubtitle : null}
            musicWaveIsPlaying={music.musicMode && isPlaying}
            musicError={music.musicError}
            cyclePlayingId={audiobook.cyclePlayingId}
            cycleIsPlaying={Boolean(audiobook.cyclePlayingId && isPlaying && !music.musicMode)}
            onQueryChange={library.setQuery}
            onCycleClick={library.setSelectedCycle}
            onCyclePlay={(cycle) => void audiobook.playCycle(cycle)}
            onFilterClick={() => library.setShowFilterSheet(true)}
            onMusicWavePlay={music.playMusicWave}
            onMusicTrackClick={(track) => void music.playMusicTrack(track)}
            onMusicTrackDownload={(track) => void music.downloadMusicTrack(track)}
            onMusicTrackDelete={(track) => void music.deleteMusicTrack(track)}
            onDownloadAllMusic={() => void music.downloadAllMusic()}
          />
          <LibraryFilterSheet
            visible={activeTab === "books" && library.showFilterSheet}
            filter={library.filter}
            onDismiss={() => library.setShowFilterSheet(false)}
            onApply={() => library.setShowFilterSheet(false)}
            onReset={library.resetFilter}
            onContentFilterChange={(contentFilter) =>
              library.setFilter((f) => ({ ...f, contentFilter }))
            }
            onSortOrderChange={(sortOrder) => library.setFilter((f) => ({ ...f, sortOrder }))}
          />
        </div>
      ) : activeTab === "downloads" ? (
        <DownloadsPage
          downloadQueue={downloadQueue.state}
          completedItems={library.completedDownloads}
          books={library.books}
          cycles={library.cycles}
          onCancelTrack={(bookId, trackId) => void downloadQueue.cancelTrack(bookId, trackId)}
          onCancelAll={() => void downloadQueue.cancelAll()}
          onDeleteCompleted={(bookId, trackId) => {
            void downloadQueue.cancelTrack(bookId, trackId);
            void deleteDownload.mutateAsync({ bookId, trackId }).then(() => library.refreshLibrary());
          }}
        />
      ) : (
        <ProfilePage
          displayName={displayName}
          email={userEmail}
          avatarUrl={avatarUrl}
          memberSinceEpochMs={memberSinceEpochMs}
          online={sessionState === "AuthenticatedOnline"}
          pendingCount={library.pendingCount}
          lastSyncAtEpochMs={library.lastSyncAtEpochMs}
          storageUsedBytes={library.storageUsed}
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
            void triggerSync
              .mutateAsync()
              .then(() => library.refreshLibrary())
              .finally(() => setSyncing(false));
          }}
          onCloseSyncDialog={() => setShowSyncDialog(false)}
          onProfileUpdated={() => void refreshSession()}
          onDeleteAllDownloads={() => void downloads.deleteAllDownloads()}
        />
      )}
      {error && <p className="error-text">{error}</p>}
    </AppShell>
  );

  return (
    <>
      {shell}
      {toastMessage && <ToastMessage message={toastMessage} />}
      <EarlierChapterPrompt
        visible={Boolean(audiobook.earlierChapterPrompt && selectedBook)}
        onCancel={audiobook.dismissEarlierChapterPrompt}
        onConfirm={audiobook.confirmEarlierChapterPrompt}
      />
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
        onSkipPrevious={audiobook.handleSkipPrevious}
        onSkipNext={audiobook.handleSkipNext}
        onSeek={seekTo}
        volume={volume}
        onVolumeChange={setVolume}
      />
      <audio ref={audioRef} className="hidden" onEnded={audiobook.handleTrackEnded} onTimeUpdate={onTimeUpdate} />
    </>
  );
}
