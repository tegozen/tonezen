import { LibraryFilterSheet } from "@/features/library-filter";
import { LibraryPage } from "@/pages/library";
import { AppShellErrorBanner } from "@/app/AppShellErrorBanner";
import type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

type AppShellLibraryRouteProps = Pick<
  AppShellRoutesProps,
  | "activeTab"
  | "sessionState"
  | "library"
  | "downloadQueue"
  | "music"
  | "audiobook"
  | "currentTrack"
  | "isPlaying"
  | "miniTitle"
  | "miniSubtitle"
  | "error"
>;

export function AppShellLibraryRoute({
  activeTab,
  sessionState,
  library,
  downloadQueue,
  music,
  audiobook,
  currentTrack,
  isPlaying,
  miniTitle,
  miniSubtitle,
  error,
}: AppShellLibraryRouteProps) {
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
      <AppShellErrorBanner error={error} />
    </>
  );
}
