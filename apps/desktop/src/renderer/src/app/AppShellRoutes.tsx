import { computeCycleCardState, isBookFullyDownloaded } from "@/entities/catalog";
import { LibraryFilterSheet } from "@/features/library-filter";
import { BookDetailPage } from "@/pages/book-detail";
import { CycleDetailPage } from "@/pages/cycle-detail";
import { DownloadsPage } from "@/pages/downloads";
import { LibraryPage } from "@/pages/library";
import { ProfilePage } from "@/pages/profile";
import type { AppShellWiring } from "@/app/useAppShellWiring";

type AppShellRoutesProps = Pick<
  AppShellWiring,
  | "activeTab"
  | "sessionState"
  | "library"
  | "downloadQueue"
  | "music"
  | "audiobook"
  | "downloads"
  | "currentTrack"
  | "isPlaying"
  | "positionMs"
  | "miniTitle"
  | "miniSubtitle"
  | "savedBookProgress"
  | "bookIsListened"
  | "openBook"
  | "deleteDownload"
  | "triggerSync"
  | "handleLogout"
  | "showSignOutConfirm"
  | "setShowSignOutConfirm"
  | "showSyncDialog"
  | "setShowSyncDialog"
  | "syncing"
  | "setSyncing"
  | "refreshSession"
  | "displayName"
  | "userEmail"
  | "avatarUrl"
  | "memberSinceEpochMs"
  | "error"
>;

export function AppShellRoutes({
  activeTab,
  sessionState,
  library,
  downloadQueue,
  music,
  audiobook,
  downloads,
  currentTrack,
  isPlaying,
  positionMs,
  miniTitle,
  miniSubtitle,
  savedBookProgress,
  bookIsListened,
  openBook,
  deleteDownload,
  triggerSync,
  handleLogout,
  showSignOutConfirm,
  setShowSignOutConfirm,
  showSyncDialog,
  setShowSyncDialog,
  syncing,
  setSyncing,
  refreshSession,
  displayName,
  userEmail,
  avatarUrl,
  memberSinceEpochMs,
  error,
}: AppShellRoutesProps) {
  const selectedBook = library.selectedBook;
  const selectedCycle = library.selectedCycle;

  if (selectedBook) {
    return (
      <>
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
        {error && <p className="error-text">{error}</p>}
      </>
    );
  }

  if (selectedCycle) {
    return (
      <>
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
        {error && <p className="error-text">{error}</p>}
      </>
    );
  }

  if (activeTab === "music" || activeTab === "books") {
    return (
      <>
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
        {error && <p className="error-text">{error}</p>}
      </>
    );
  }

  if (activeTab === "downloads") {
    return (
      <>
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
        {error && <p className="error-text">{error}</p>}
      </>
    );
  }

  return (
    <>
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
      {error && <p className="error-text">{error}</p>}
    </>
  );
}
