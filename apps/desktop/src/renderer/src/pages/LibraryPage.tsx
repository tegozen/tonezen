import type { Cycle } from "@shared/types";
import type { MusicListTrack } from "@shared/musicList";
import type { DownloadQueueState } from "@shared/downloadQueueState";
import { libraryScrollPaddingTop } from "../lib/layoutChrome";
import { LibraryCycleCard } from "../components/LibraryCycleCard";
import { LibraryTopChrome } from "../components/LibraryTopChrome";
import { MusicDownloadAllButton } from "../components/MusicDownloadAllButton";
import { MusicTrackRow } from "../components/MusicTrackRow";
import { DownloadsPage } from "./DownloadsPage";
import type { CycleCardState } from "../lib/cycleUtils";
import { strings } from "../i18n/strings";

interface LibraryPageProps {
  cycles: Cycle[];
  cycleCardStateById: Record<string, CycleCardState>;
  musicTracks: MusicListTrack[];
  query: string;
  selectedTab: number;
  offlineBanner: boolean;
  isLoading: boolean;
  downloadQueue: DownloadQueueState;
  activeMusicTrackId: string | null;
  musicError: string | null;
  cyclePlayingId: string | null;
  cycleIsPlaying: boolean;
  onQueryChange: (value: string) => void;
  onTabChange: (tab: number) => void;
  onCycleClick: (cycle: Cycle) => void;
  onCyclePlay: (cycle: Cycle) => void;
  onFilterClick: () => void;
  onMusicTrackClick: (track: MusicListTrack) => void;
  onMusicTrackDownload: (track: MusicListTrack) => void;
  onMusicTrackDelete: (track: MusicListTrack) => void;
  onDownloadAllMusic: () => void;
  onCancelDownloadTrack: (bookId: string, trackId: string) => void;
  onCancelAllDownloads: () => void;
  onDeleteCompletedDownload: (bookId: string, trackId: string) => void;
}

export function LibraryPage({
  cycles,
  cycleCardStateById,
  musicTracks,
  query,
  selectedTab,
  offlineBanner,
  isLoading,
  downloadQueue,
  activeMusicTrackId,
  musicError,
  cyclePlayingId,
  cycleIsPlaying,
  onQueryChange,
  onTabChange,
  onCycleClick,
  onCyclePlay,
  onFilterClick,
  onMusicTrackClick,
  onMusicTrackDownload,
  onMusicTrackDelete,
  onDownloadAllMusic,
  onCancelDownloadTrack,
  onCancelAllDownloads,
  onDeleteCompletedDownload,
}: LibraryPageProps) {
  const isAudiobooks = selectedTab === 0;
  const isMusic = selectedTab === 1;
  const isDownloads = selectedTab === 2;

  if (isDownloads) {
    return (
      <DownloadsPage
        downloadQueue={downloadQueue}
        selectedTab={selectedTab}
        offlineBanner={offlineBanner}
        onTabChange={onTabChange}
        onCancelTrack={onCancelDownloadTrack}
        onCancelAll={onCancelAllDownloads}
        onDeleteCompleted={onDeleteCompletedDownload}
      />
    );
  }

  return (
    <div className="library-page">
      <div
        className="scroll-under-chrome space-y-5"
        style={{ paddingTop: libraryScrollPaddingTop(isAudiobooks, offlineBanner) }}
      >
      {isAudiobooks ? (
        isLoading ? (
          <p className="text-center text-muted">{strings.libraryLoading}</p>
        ) : cycles.length === 0 ? (
          <EmptyLibrary offline={offlineBanner} />
        ) : (
          <div className="library-cycle-grid">
            {cycles.map((cycle) => {
              const state = cycleCardStateById[cycle.id] ?? {
                isDownloaded: false,
                progressFraction: null,
                continueState: null,
                showDownload: true,
                showRemoveDownload: false,
                isListened: false,
              };
              const isPlayingThis = cyclePlayingId === cycle.id && cycleIsPlaying;
              return (
                <LibraryCycleCard
                  key={cycle.id}
                  cycle={cycle}
                  state={state}
                  isPlaying={isPlayingThis}
                  onClick={() => onCycleClick(cycle)}
                  onPlayClick={() => onCyclePlay(cycle)}
                />
              );
            })}
          </div>
        )
      ) : isMusic && isLoading ? (
        <p className="text-center text-muted">{strings.libraryLoading}</p>
      ) : isMusic && musicTracks.length === 0 ? (
        <EmptyLibrary offline={offlineBanner} />
      ) : isMusic ? (
        <div className="music-library-stack">
          <MusicDownloadAllButton
            tracks={musicTracks}
            musicDownload={downloadQueue}
            onClick={onDownloadAllMusic}
          />
          {musicError && <p className="text-sm text-error">{musicError}</p>}
          <div className="music-track-list">
            {musicTracks.map((track) => (
              <MusicTrackRow
                key={track.trackId}
                track={track}
                isActive={activeMusicTrackId === track.trackId}
                downloadQueue={downloadQueue}
                onClick={() => onMusicTrackClick(track)}
                onDownloadClick={() => onMusicTrackDownload(track)}
                onDeleteClick={() => onMusicTrackDelete(track)}
              />
            ))}
          </div>
        </div>
      ) : null}
      </div>
      <LibraryTopChrome
        selectedTab={selectedTab}
        query={query}
        offlineBanner={offlineBanner}
        showSearch={isAudiobooks}
        onTabChange={onTabChange}
        onQueryChange={onQueryChange}
        onFilterClick={onFilterClick}
      />
    </div>
  );
}

function EmptyLibrary({ offline }: { offline: boolean }) {
  return (
    <div className="py-12 text-center">
      <h2 className="text-lg font-semibold">{strings.emptyLibraryTitle}</h2>
      <p className="mt-2 text-sm text-muted">
        {offline ? strings.emptyLibraryOfflineBody : strings.emptyLibraryBody}
      </p>
    </div>
  );
}
