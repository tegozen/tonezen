import {
  Forward15Icon,
  PauseIcon,
  PlayIcon,
  Rewind15Icon,
  SkipBackIcon,
  SkipForwardIcon,
} from "./TonezenIcons";
import { TrackCoverArt } from "./CoverArt";
import { PlayingBars } from "./PlayingBars";
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
  onDismiss,
  onPlayPause,
  onSeekBy,
  onSkipPrevious,
  onSkipNext,
  onSeek,
}: NowPlayingSheetProps) {
  if (!visible) return null;

  const progress = durationMs > 0 ? positionMs / durationMs : 0;

  return (
    <div className="sheet-overlay" onClick={onDismiss}>
      <div className="sheet-panel glass-panel now-playing-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="flex flex-col items-center gap-6 pt-2">
          <TrackCoverArt seed={coverSeed} title={title} className="h-44 w-44">
            {isPlaying && (
              <div className="absolute inset-0 flex items-center justify-center bg-black/20">
                <PlayingBars active />
              </div>
            )}
          </TrackCoverArt>
          <div className="w-full text-center">
            <h2 className="truncate text-xl font-bold">{title}</h2>
            <p className="truncate text-sm text-muted">{subtitle}</p>
          </div>
          <div className="w-full space-y-2">
            <input
              type="range"
              min={0}
              max={1000}
              value={Math.round(progress * 1000)}
              className="seek-slider w-full"
              onChange={(e) => onSeek(Number(e.target.value) / 1000)}
              aria-label={strings.nowPlaying}
            />
            <div className="flex justify-between text-xs text-muted">
              <span>{formatMs(positionMs)}</span>
              <span>-{formatMs(Math.max(durationMs - positionMs, 0))}</span>
            </div>
          </div>
          <div className="flex items-center justify-center gap-4">
            <button
              type="button"
              className="round-control"
              onClick={() => onSeekBy(-15000)}
              aria-label={strings.rewind15}
            >
              <Rewind15Icon className="h-6 w-6" />
            </button>
            <button type="button" className="round-control" onClick={onSkipPrevious} aria-label={strings.back}>
              <SkipBackIcon className="h-6 w-6" />
            </button>
            <button type="button" className="btn-play" onClick={onPlayPause} aria-label={isPlaying ? strings.pause : strings.play}>
              {isPlaying ? <PauseIcon className="h-8 w-8" /> : <PlayIcon className="h-8 w-8" />}
            </button>
            <button type="button" className="round-control" onClick={onSkipNext} aria-label={isMusic ? strings.tabMusic : strings.chapters}>
              <SkipForwardIcon className="h-6 w-6" />
            </button>
            <button
              type="button"
              className="round-control"
              onClick={() => onSeekBy(15000)}
              aria-label={strings.forward15}
            >
              <Forward15Icon className="h-6 w-6" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
