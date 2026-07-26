import type { CSSProperties, PointerEvent as ReactPointerEvent, RefObject } from "react";
import { formatMs } from "@/shared/lib/formatTime";

interface NowPlayingSeekBarProps {
  progressRef: RefObject<HTMLDivElement | null>;
  progress: number;
  progressPercent: string;
  positionMs: number;
  durationMs: number;
  disabled: boolean;
  activeWaveformPeaks: number[] | null;
  renderWaveformBars: (keyPrefix: string) => React.ReactNode;
  onPointerDown: (event: ReactPointerEvent<HTMLDivElement>) => void;
  onPointerMove: (event: ReactPointerEvent<HTMLDivElement>) => void;
  onPointerUp: (event: ReactPointerEvent<HTMLDivElement>) => void;
  onSeek: (fraction: number) => void;
}

export function NowPlayingSeekBar({
  progressRef,
  progress,
  progressPercent,
  positionMs,
  durationMs,
  disabled,
  activeWaveformPeaks,
  renderWaveformBars,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onSeek,
}: NowPlayingSeekBarProps) {
  return (
    <div className="now-playing-sheet-progress-block">
      <div
        ref={progressRef}
        className={activeWaveformPeaks ? "now-playing-waveform" : "now-playing-progress"}
        style={
          activeWaveformPeaks
            ? ({ "--waveform-progress": progressPercent } as CSSProperties)
            : undefined
        }
        role="slider"
        aria-label="Сейчас играет"
        aria-valuemin={0}
        aria-valuemax={1000}
        aria-valuenow={Math.round(progress * 1000)}
        tabIndex={disabled ? -1 : 0}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onKeyDown={(event) => {
          if (disabled) return;
          if (event.key === "ArrowRight") onSeek(Math.min(1, progress + 0.05));
          if (event.key === "ArrowLeft") onSeek(Math.max(0, progress - 0.05));
        }}
      >
        {activeWaveformPeaks ? (
          <>
            <div className="now-playing-waveform-bars now-playing-waveform-bars-muted" aria-hidden="true">
              {renderWaveformBars("muted")}
            </div>
            <div className="now-playing-waveform-bars now-playing-waveform-bars-active" aria-hidden="true">
              {renderWaveformBars("active")}
            </div>
          </>
        ) : (
          <>
            <div className="now-playing-progress-fill" style={{ width: progressPercent }} />
            <div className="now-playing-progress-thumb" style={{ left: progressPercent }} aria-hidden="true" />
          </>
        )}
      </div>
      <div className="flex justify-between text-xs text-muted">
        <span>{formatMs(positionMs)}</span>
        <span>{formatMs(durationMs)}</span>
      </div>
    </div>
  );
}
