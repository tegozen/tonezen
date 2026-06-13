import { PauseIcon, PlayIcon, SkipBackIcon, SkipForwardIcon } from "./TonezenIcons";
import { TrackCoverArt } from "./CoverArt";
import { useAnimatedVisibility } from "../hooks/useAnimatedVisibility";
import { formatMs } from "../lib/formatTime";
import { strings } from "../i18n/strings";

interface NowPlayingSheetProps {
  visible: boolean;
  title: string;
  subtitle: string;
  coverSeed: string;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  isMusic: boolean;
  downloadProgress?: number | null;
  controlsDisabled?: boolean;
  onDismiss: () => void;
  onPlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onSkipPrevious: () => void;
  onSkipNext: () => void;
  onSeek: (fraction: number) => void;
}

export function NowPlayingSheet({
  visible,
  title,
  subtitle,
  coverSeed,
  isPlaying,
  positionMs,
  durationMs,
  isMusic,
  downloadProgress = null,
  controlsDisabled = false,
  onDismiss,
  onPlayPause,
  onSeekBy,
  onSkipPrevious,
  onSkipNext,
  onSeek,
}: NowPlayingSheetProps) {
  const { mounted, open } = useAnimatedVisibility(visible, 320);
  const progress = durationMs > 0 ? positionMs / durationMs : 0;
  const isDownloading = downloadProgress != null;
  const showDownloadLabel = isDownloading && downloadProgress > 0;
  const disabled = controlsDisabled || isDownloading;
  const coverPlaying = isPlaying && !isDownloading;

  if (!mounted) return null;

  return (
    <div
      className={`now-playing-sheet-overlay ${open ? "now-playing-sheet-overlay-open" : ""}`}
      onClick={onDismiss}
    >
      <div
        className={`now-playing-sheet-panel ${open ? "now-playing-sheet-panel-open" : ""}`}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={strings.nowPlaying}
      >
        <div className="now-playing-sheet-glass">
          <div className="now-playing-sheet-handle" />
          <div className="now-playing-sheet-content">
            <div className="now-playing-sheet-hero">
              <TrackCoverArt
                seed={coverSeed}
                title={title}
                showInitial
                isPlaying={coverPlaying}
                className="h-[168px] w-[168px] rounded-[24px]"
              >
                {isDownloading ? (
                  <div className="absolute inset-0 flex items-center justify-center bg-black/55 text-base font-bold text-teal">
                    {showDownloadLabel ? `${Math.round(downloadProgress * 100)}%` : "…"}
                  </div>
                ) : null}
              </TrackCoverArt>
              <div className="now-playing-sheet-meta">
                <h2 className="line-clamp-2">{title}</h2>
                {subtitle ? <p className="line-clamp-2">{subtitle}</p> : null}
              </div>
            </div>

            <div className="now-playing-sheet-progress-block">
              <div
                className="now-playing-progress"
                role="slider"
                aria-label={strings.nowPlaying}
                aria-valuemin={0}
                aria-valuemax={1000}
                aria-valuenow={Math.round(progress * 1000)}
                tabIndex={disabled ? -1 : 0}
                onClick={(event) => {
                  if (disabled) return;
                  const rect = event.currentTarget.getBoundingClientRect();
                  onSeek((event.clientX - rect.left) / rect.width);
                }}
                onKeyDown={(event) => {
                  if (disabled) return;
                  if (event.key === "ArrowRight") onSeek(Math.min(1, progress + 0.05));
                  if (event.key === "ArrowLeft") onSeek(Math.max(0, progress - 0.05));
                }}
              >
                <div className="now-playing-progress-fill" style={{ width: `${progress * 100}%` }} />
              </div>
              <div className="flex justify-between text-xs text-muted">
                <span>{formatMs(positionMs)}</span>
                <span>{formatMs(durationMs)}</span>
              </div>
            </div>

            <div className="now-playing-sheet-controls">
              <button
                type="button"
                className="now-playing-round-control now-playing-round-control-sm"
                disabled={disabled}
                onClick={() => onSeekBy(-15000)}
                aria-label={strings.rewind15}
              >
                {strings.seekBack15}
              </button>
              <button
                type="button"
                className="now-playing-round-control"
                disabled={disabled}
                onClick={onSkipPrevious}
                aria-label={strings.back}
              >
                <SkipBackIcon className="h-6 w-6" />
              </button>
              <button
                type="button"
                className={`now-playing-play-btn ${coverPlaying ? "now-playing-play-btn-playing" : ""}`}
                disabled={isDownloading}
                onClick={onPlayPause}
                aria-label={isPlaying ? strings.pause : strings.play}
              >
                {isDownloading ? (
                  <span className="text-sm font-bold">
                    {showDownloadLabel ? `${Math.round(downloadProgress * 100)}%` : "…"}
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
                aria-label={isMusic ? strings.tabMusic : strings.chapters}
              >
                <SkipForwardIcon className="h-6 w-6" />
              </button>
              <button
                type="button"
                className="now-playing-round-control now-playing-round-control-sm"
                disabled={disabled}
                onClick={() => onSeekBy(15000)}
                aria-label={strings.forward15}
              >
                {strings.seekForward15}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
