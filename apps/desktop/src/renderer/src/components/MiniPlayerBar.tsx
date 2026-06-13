import { PauseIcon, PlayIcon } from "./TonezenIcons";
import { TrackCoverArt } from "./CoverArt";

interface MiniPlayerBarProps {
  title: string | null;
  subtitle: string | null;
  isPlaying: boolean;
  progress: number;
  onBarClick: () => void;
  onPlayPause: () => void;
}

export function MiniPlayerBar({
  title,
  subtitle,
  isPlaying,
  progress,
  onBarClick,
  onPlayPause,
}: MiniPlayerBarProps) {
  if (!title) return null;
  return (
    <div className="mini-player">
      <button type="button" className="mini-player-row" onClick={onBarClick}>
        <TrackCoverArt seed={title} title={title} className="h-12 w-12 shrink-0 rounded-lg" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{title}</div>
          <div className="truncate text-xs text-muted">{subtitle}</div>
        </div>
        <button
          type="button"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-[0] text-ink"
          onClick={(e) => {
            e.stopPropagation();
            onPlayPause();
          }}
        >
          {isPlaying ? <PauseIcon className="h-5 w-5 text-base" /> : <PlayIcon className="h-5 w-5 text-base" />}
        </button>
      </button>
      <div className="mini-player-progress">
        <div className="mini-player-progress-fill" style={{ width: `${Math.min(progress * 100, 100)}%` }} />
      </div>
    </div>
  );
}
