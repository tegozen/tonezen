import { PauseIcon, PlayIcon } from "@/shared/ui/TonezenIcons";
import { TrackCoverArt } from "@/shared/ui/CoverArt";

interface MiniPlayerBarProps {
  title: string | null;
  subtitle: string | null;
  coverSeed?: string;
  isPlaying: boolean;
  progress: number;
  downloadProgress?: number | null;
  onBarClick: () => void;
  onPlayPause: () => void;
}

export function MiniPlayerBar({
  title,
  subtitle,
  coverSeed,
  isPlaying,
  progress,
  downloadProgress = null,
  onBarClick,
  onPlayPause,
}: MiniPlayerBarProps) {
  if (!title) return null;
  const isDownloading = downloadProgress != null;
  const showProgressLabel = isDownloading && downloadProgress > 0;

  return (
    <div className="mini-player">
      <div className="mini-player-row">
        <button type="button" className="mini-player-main" onClick={onBarClick}>
          <div className="relative shrink-0">
            <TrackCoverArt seed={coverSeed ?? title} title={title} className="h-12 w-12 rounded-lg" />
            {isDownloading && (
              <div className="absolute inset-0 flex items-center justify-center rounded-lg bg-black/45 text-[10px] font-bold text-teal">
                {showProgressLabel ? `${Math.round(downloadProgress * 100)}%` : "…"}
              </div>
            )}
          </div>
          <div className="min-w-0 flex-1 text-left">
            <div className="truncate text-sm font-medium">{title}</div>
            <div className="truncate text-xs text-muted">{subtitle}</div>
          </div>
        </button>
        <button
          type="button"
          className={`mini-player-play-btn ${isPlaying ? "mini-player-play-btn-playing" : ""}`}
          disabled={isDownloading}
          aria-label={isPlaying ? "Пауза" : "Воспроизвести"}
          onClick={onPlayPause}
        >
          {isPlaying ? <PauseIcon className="h-[30px] w-[30px]" /> : <PlayIcon className="h-[30px] w-[30px]" />}
        </button>
      </div>
      <div className="mini-player-progress">
        <div className="mini-player-progress-fill" style={{ width: `${Math.min(progress * 100, 100)}%` }} />
      </div>
    </div>
  );
}
