import { useState } from "react";
import type { Cycle } from "@shared/types";
import type { MusicListTrack } from "@shared/musicList";
import type { DownloadQueueState } from "@shared/downloadQueueState";
import { libraryScrollPaddingTop, type LibrarySection } from "../lib/layoutChrome";
import { LibraryCycleCard } from "../components/LibraryCycleCard";
import { LibraryTopChrome } from "../components/LibraryTopChrome";
import { MusicDownloadAllButton } from "../components/MusicDownloadAllButton";
import { MusicTrackRow } from "../components/MusicTrackRow";
import { TrackSpectrumArt } from "../components/CoverArt";
import { PauseIcon, PlayIcon } from "../components/TonezenIcons";
import type { CycleCardState } from "../lib/cycleUtils";
import { strings } from "../i18n/strings";

interface LibraryPageProps {
  cycles: Cycle[];
  cycleCardStateById: Record<string, CycleCardState>;
  musicTracks: MusicListTrack[];
  query: string;
  section: LibrarySection;
  offlineBanner: boolean;
  isLoading: boolean;
  downloadQueue: DownloadQueueState;
  activeMusicTrackId: string | null;
  musicWaveTitle: string | null;
  musicWaveSubtitle: string | null;
  musicWaveIsPlaying: boolean;
  musicError: string | null;
  cyclePlayingId: string | null;
  cycleIsPlaying: boolean;
  onQueryChange: (value: string) => void;
  onCycleClick: (cycle: Cycle) => void;
  onCyclePlay: (cycle: Cycle) => void;
  onFilterClick: () => void;
  onMusicWavePlay: () => void;
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
  section,
  offlineBanner,
  isLoading,
  downloadQueue,
  activeMusicTrackId,
  musicWaveTitle,
  musicWaveSubtitle,
  musicWaveIsPlaying,
  musicError,
  cyclePlayingId,
  cycleIsPlaying,
  onQueryChange,
  onCycleClick,
  onCyclePlay,
  onFilterClick,
  onMusicWavePlay,
  onMusicTrackClick,
  onMusicTrackDownload,
  onMusicTrackDelete,
  onDownloadAllMusic,
}: LibraryPageProps) {
  const isBooks = section === "books";
  const isMusic = section === "music";
  const [showAllMusicTracks, setShowAllMusicTracks] = useState(false);

  return (
    <div className="library-page">
      <div
        className="scroll-under-chrome space-y-5"
        style={{ paddingTop: libraryScrollPaddingTop(section, offlineBanner) }}
      >
      {isBooks ? (
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
          <MusicWaveCard
            tracks={musicTracks}
            title={musicWaveTitle}
            subtitle={musicWaveSubtitle}
            activeTrackId={activeMusicTrackId}
            isPlaying={musicWaveIsPlaying}
            onClick={onMusicWavePlay}
          />
          <MusicDownloadAllButton
            tracks={musicTracks}
            musicDownload={downloadQueue}
            onClick={onDownloadAllMusic}
          />
          {musicError && <p className="text-sm text-error">{musicError}</p>}
          <button
            type="button"
            className="music-all-tracks-toggle"
            onClick={() => setShowAllMusicTracks((value) => !value)}
          >
            <span>{strings.musicAllTracks}</span>
            <span>{showAllMusicTracks ? "-" : "+"} {musicTracks.length}</span>
          </button>
          {showAllMusicTracks ? (
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
          ) : null}
        </div>
      ) : null}
      </div>
      <LibraryTopChrome
        title={isBooks ? strings.navBooks : strings.navMusic}
        query={query}
        offlineBanner={offlineBanner}
        showSearch={isBooks}
        onQueryChange={onQueryChange}
        onFilterClick={onFilterClick}
      />
    </div>
  );
}

function MusicWaveCard({
  tracks,
  title,
  subtitle,
  activeTrackId,
  isPlaying,
  onClick,
}: {
  tracks: MusicListTrack[];
  title: string | null;
  subtitle: string | null;
  activeTrackId: string | null;
  isPlaying: boolean;
  onClick: () => void;
}) {
  const fallback = tracks[0];
  const displayTitle = title ?? fallback?.trackTitle ?? strings.musicWavePlay;
  const displaySubtitle = subtitle ?? fallback?.artist ?? strings.musicWaveTracksCount(tracks.length);
  const coverSeed = activeTrackId ?? fallback?.trackId;

  return (
    <button type="button" className="music-wave-card" onClick={onClick}>
      <TrackSpectrumArt
        seed={coverSeed ?? "music-wave"}
        title={displayTitle}
        isPlaying={isPlaying}
        className="music-wave-cover"
      />
      <div className="music-wave-copy">
        <p className="music-wave-eyebrow">{strings.musicWaveTitle}</p>
        <h2>{displayTitle}</h2>
        <p>{displaySubtitle}</p>
        <span>{strings.musicWaveTracksCount(tracks.length)}</span>
      </div>
      <span className={`music-wave-play ${isPlaying ? "music-wave-play-active" : ""}`}>
        {isPlaying ? <PauseIcon className="h-7 w-7" /> : <PlayIcon className="h-7 w-7" />}
      </span>
    </button>
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
