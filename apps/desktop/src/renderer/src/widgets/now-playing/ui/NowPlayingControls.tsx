import { PauseIcon, PlayIcon, SkipBackIcon, SkipForwardIcon } from "@/shared/ui/TonezenIcons";

interface NowPlayingControlsProps {
  isPlaying: boolean;
  isMusic: boolean;
  disabled: boolean;
  isDownloading: boolean;
  showDownloadLabel: boolean;
  downloadProgress: number | null;
  coverPlaying: boolean;
  onPlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onSkipPrevious: () => void;
  onSkipNext: () => void;
}

export function NowPlayingControls({
  isPlaying,
  isMusic,
  disabled,
  isDownloading,
  showDownloadLabel,
  downloadProgress,
  coverPlaying,
  onPlayPause,
  onSeekBy,
  onSkipPrevious,
  onSkipNext,
}: NowPlayingControlsProps) {
  return (
    <div className="now-playing-sheet-controls">
      <button
        type="button"
        className="now-playing-round-control now-playing-round-control-sm"
        disabled={disabled}
        onClick={() => onSeekBy(-15000)}
        aria-label="Перемотка назад на 15 секунд"
      >
        -15
      </button>
      <button
        type="button"
        className="now-playing-round-control"
        disabled={disabled}
        onClick={onSkipPrevious}
        aria-label="Назад"
      >
        <SkipBackIcon className="h-6 w-6" />
      </button>
      <button
        type="button"
        className={`now-playing-play-btn ${coverPlaying ? "now-playing-play-btn-playing" : ""}`}
        disabled={isDownloading}
        onClick={onPlayPause}
        aria-label={isPlaying ? "Пауза" : "Воспроизвести"}
      >
        {isDownloading ? (
          <span className="text-sm font-bold">
            {showDownloadLabel && downloadProgress != null
              ? `${Math.round(downloadProgress * 100)}%`
              : "…"}
          </span>
        ) : isPlaying ? (
          <PauseIcon className="h-8 w-8" />
        ) : (
          <PlayIcon className="h-8 w-8" />
        )}
      </button>
      <button
        type="button"
        className="now-playing-round-control"
        disabled={disabled}
        onClick={onSkipNext}
        aria-label={isMusic ? "Музыка" : "Главы"}
      >
        <SkipForwardIcon className="h-6 w-6" />
      </button>
      <button
        type="button"
        className="now-playing-round-control now-playing-round-control-sm"
        disabled={disabled}
        onClick={() => onSeekBy(15000)}
        aria-label="Перемотка вперёд на 15 секунд"
      >
        +15
      </button>
    </div>
  );
}
