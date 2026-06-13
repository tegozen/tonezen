import type { Cycle } from "@shared/types";
import type { MusicListTrack } from "@shared/musicList";
import {
  isMusicDownloadActive,
  progressForTrack,
  type MusicDownloadState,
} from "@shared/musicDownloadState";
import { LibraryCycleCard } from "../components/LibraryCycleCard";
import { LibraryTopChrome } from "../components/LibraryTopChrome";
import { MusicDownloadAllButton } from "../components/MusicDownloadAllButton";
import { MusicTrackRow } from "../components/MusicTrackRow";
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
  musicDownload: MusicDownloadState;
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
}

export function LibraryPage({
  cycles,
  cycleCardStateById,
  musicTracks,
  query,
  selectedTab,
  offlineBanner,
  isLoading,
  musicDownload,
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
}: LibraryPageProps) {
  const isAudiobooks = selectedTab === 0;
  const downloadActive = isMusicDownloadActive(musicDownload);

  return (
    <div>
      <LibraryTopChrome
        selectedTab={selectedTab}
        query={query}
        offlineBanner={offlineBanner}
        showSearch={isAudiobooks}
        onTabChange={onTabChange}
        onQueryChange={onQueryChange}
        onFilterClick={onFilterClick}
      />
      <div className="library-content">
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
      ) : musicTracks.length === 0 && !isLoading ? (
        <EmptyLibrary offline={offlineBanner} />
      ) : (
        <div className="music-library-stack">
          <MusicDownloadAllButton
            tracks={musicTracks}
            musicDownload={musicDownload}
            onClick={onDownloadAllMusic}
          />
          {musicError && <p className="text-sm text-error">{musicError}</p>}
          <div className="music-track-list">
            {musicTracks.map((track) => {
              const trackDownloadProgress = progressForTrack(musicDownload, track.trackId);
              return (
                <MusicTrackRow
                  key={track.trackId}
                  track={track}
                  isActive={activeMusicTrackId === track.trackId}
                  downloadProgress={trackDownloadProgress}
                  downloadActive={downloadActive}
                  onClick={() => onMusicTrackClick(track)}
                  onDownloadClick={() => onMusicTrackDownload(track)}
                  onDeleteClick={() => onMusicTrackDelete(track)}
                />
              );
            })}
          </div>
        </div>
      )}
      </div>
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
