import type { Cycle } from "@shared/types";
import type { MusicListTrack } from "@shared/musicList";
import {
  PauseIcon,
  PlayIcon,
  TrashIcon,
} from "../components/TonezenIcons";
import { CoverArt } from "../components/CoverArt";
import { LibraryCycleCard } from "../components/LibraryCycleCard";
import { LibraryTopChrome } from "../components/LibraryTopChrome";
import type { CycleCardState } from "../lib/cycleUtils";
import { formatMs } from "../lib/formatTime";
import { strings } from "../i18n/strings";

interface LibraryPageProps {
  cycles: Cycle[];
  cycleCardStateById: Record<string, CycleCardState>;
  musicTracks: MusicListTrack[];
  query: string;
  selectedTab: number;
  offlineBanner: boolean;
  isLoading: boolean;
  musicDownloadProgress: { done: number; total: number } | null;
  musicDownloadingTrackId: string | null;
  activeMusicTrackId: string | null;
  isMusicPlaying: boolean;
  musicError: string | null;
  cyclePlayingId: string | null;
  cycleIsPlaying: boolean;
  onQueryChange: (value: string) => void;
  onTabChange: (tab: number) => void;
  onCycleClick: (cycle: Cycle) => void;
  onCyclePlay: (cycle: Cycle) => void;
  onFilterClick: () => void;
  onMusicTrackClick: (track: MusicListTrack) => void;
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
  musicDownloadProgress,
  musicDownloadingTrackId,
  activeMusicTrackId,
  isMusicPlaying,
  musicError,
  cyclePlayingId,
  cycleIsPlaying,
  onQueryChange,
  onTabChange,
  onCycleClick,
  onCyclePlay,
  onFilterClick,
  onMusicTrackClick,
  onMusicTrackDelete,
  onDownloadAllMusic,
}: LibraryPageProps) {
  const isAudiobooks = selectedTab === 0;

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
        <div className="space-y-3">
          {musicTracks.some((t) => !t.isDownloaded) && (
            <button
              type="button"
              className="btn-secondary w-full"
              disabled={musicDownloadProgress != null}
              onClick={onDownloadAllMusic}
            >
              {musicDownloadProgress
                ? strings.musicDownloadAllProgress(musicDownloadProgress.done, musicDownloadProgress.total)
                : strings.musicDownloadAll}
            </button>
          )}
          {musicError && <p className="text-sm text-error">{musicError}</p>}
          <div className="space-y-1">
            {musicTracks.map((track) => {
              const isActive = activeMusicTrackId === track.trackId;
              const isDownloading = musicDownloadingTrackId === track.trackId;
              return (
                <div key={track.trackId} className={`track-row ${isActive ? "track-row-active" : ""}`}>
                  <CoverArt seed={track.trackId} audiobook={false} className="h-12 w-12 shrink-0 rounded-xl" />
                  <button
                    type="button"
                    className="min-w-0 flex-1 cursor-pointer text-left"
                    onClick={() => onMusicTrackClick(track)}
                    disabled={isDownloading}
                  >
                    <div className="truncate font-medium">{track.trackTitle}</div>
                    <div className="truncate text-sm text-muted">{track.artist}</div>
                  </button>
                  <span className="shrink-0 text-sm text-muted">{formatMs(track.durationMs ?? 0)}</span>
                  {track.isDownloaded ? (
                    <button
                      type="button"
                      className="icon-button h-9 w-9 shrink-0 text-error"
                      onClick={() => onMusicTrackDelete(track)}
                      aria-label={strings.musicDeleteTrack}
                    >
                      <TrashIcon className="h-4 w-4" />
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="compact-play-btn shrink-0"
                      onClick={() => onMusicTrackClick(track)}
                      disabled={isDownloading}
                      aria-label={isActive && isMusicPlaying ? strings.pause : strings.play}
                    >
                      {isDownloading ? (
                        <span className="text-xs">{strings.downloading}</span>
                      ) : isActive && isMusicPlaying ? (
                        <PauseIcon className="h-4 w-4" />
                      ) : (
                        <PlayIcon className="h-4 w-4" />
                      )}
                    </button>
                  )}
                </div>
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
