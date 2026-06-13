import type { Track } from "@shared/types";
import { CheckCircleIcon } from "./TonezenIcons";
import { PlayingBars } from "./PlayingBars";
import { TrackListRow } from "./TrackListRow";
import { TrackRowOverflowMenu } from "./TrackRowOverflowMenu";
import { strings } from "../i18n/strings";

interface ChapterTrackRowProps {
  track: Track;
  trackNumber: number;
  isActive: boolean;
  listenProgress: number | null;
  listenPercent: number | null;
  isDownloaded: boolean;
  onClick: () => void;
  onToggleListened: () => void;
  onRemoveDownload: () => void;
}

export function ChapterTrackRow({
  track,
  trackNumber,
  isActive,
  listenProgress,
  listenPercent,
  isDownloaded,
  onClick,
  onToggleListened,
  onRemoveDownload,
}: ChapterTrackRowProps) {
  const isListened = listenPercent === 100;

  return (
    <TrackListRow
      title={track.title}
      durationMs={track.durationMs}
      isActive={isActive}
      listenProgress={listenProgress}
      onClick={onClick}
      leading={
        <div className="flex items-center gap-2">
          {isActive ? (
            <PlayingBars active />
          ) : listenPercent != null ? (
            <span className="text-xs font-semibold text-teal">
              {strings.cycleListenProgress(listenPercent)}
            </span>
          ) : null}
          <span
            className={`text-sm ${
              isActive ? "text-amber" : listenPercent != null ? "text-teal" : "text-muted"
            }`}
          >
            {trackNumber}
          </span>
        </div>
      }
      trailing={
        <>
          {isDownloaded && <CheckCircleIcon className="h-[18px] w-[18px] shrink-0 text-teal" aria-label={strings.offline} />}
          <TrackRowOverflowMenu
            showDelete={isDownloaded}
            isListened={isListened}
            onToggleListened={onToggleListened}
            onDelete={onRemoveDownload}
          />
        </>
      }
    />
  );
}
