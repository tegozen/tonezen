import type { CSSProperties, PointerEvent as ReactPointerEvent } from "react";
import { useCallback, useRef } from "react";
import { useAnimatedVisibility } from "@/shared/lib/useAnimatedVisibility";
import { seekFractionFromPointer } from "@core/playback/playbackSeek";
import { normalizeWaveformPeaks } from "@core/catalog/waveformPeaks";
import { NowPlayingHero } from "./NowPlayingHero";
import { NowPlayingSeekBar } from "./NowPlayingSeekBar";
import { NowPlayingControls } from "./NowPlayingControls";

interface NowPlayingSheetProps {
  visible: boolean;
  title: string;
  subtitle: string;
  coverSeed: string;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  isMusic: boolean;
  waveformPeaks?: number[] | null;
  downloadProgress?: number | null;
  controlsDisabled?: boolean;
  onDismiss: () => void;
  onPlayPause: () => void;
  onSeekBy: (deltaMs: number) => void;
  onSkipPrevious: () => void;
  onSkipNext: () => void;
  onSeek: (fraction: number) => void;
  volume: number;
  onVolumeChange: (volume: number) => void;
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
  waveformPeaks = null,
  downloadProgress = null,
  controlsDisabled = false,
  onDismiss,
  onPlayPause,
  onSeekBy,
  onSkipPrevious,
  onSkipNext,
  onSeek,
  volume,
  onVolumeChange,
}: NowPlayingSheetProps) {
  const { mounted, open } = useAnimatedVisibility(visible, 320);
  const progress = durationMs > 0 ? positionMs / durationMs : 0;
  const isDownloading = downloadProgress != null;
  const showDownloadLabel = isDownloading && downloadProgress > 0;
  const disabled = controlsDisabled || isDownloading;
  const coverPlaying = isPlaying && !isDownloading;
  const volumePercent = Math.round(volume * 100);
  const progressRef = useRef<HTMLDivElement>(null);
  const activeWaveformPeaks = normalizeWaveformPeaks(waveformPeaks);
  const progressPercent = `${progress * 100}%`;

  const renderWaveformBars = (keyPrefix: string) =>
    activeWaveformPeaks?.map((peak, index) => (
      <span
        key={`${keyPrefix}-${index}`}
        className="now-playing-waveform-bar"
        style={{ "--waveform-peak": `${Math.max(6, peak)}%` } as CSSProperties}
      />
    ));

  const seekFromClientX = useCallback(
    (clientX: number) => {
      const el = progressRef.current;
      if (!el || disabled) return;
      const rect = el.getBoundingClientRect();
      if (rect.width <= 0) return;
      const fraction = seekFractionFromPointer(clientX, rect.left, rect.width);
      onSeek(fraction);
    },
    [disabled, onSeek],
  );

  const handleProgressPointerDown = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      if (disabled) return;
      event.preventDefault();
      event.currentTarget.setPointerCapture(event.pointerId);
      seekFromClientX(event.clientX);
    },
    [disabled, seekFromClientX],
  );

  const handleProgressPointerMove = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;
      seekFromClientX(event.clientX);
    },
    [seekFromClientX],
  );

  const handleProgressPointerUp = useCallback((event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }, []);

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
        aria-label="Сейчас играет"
      >
        <div className="now-playing-sheet-glass">
          <div className="now-playing-sheet-handle" />
          <div className="now-playing-sheet-content">
            <NowPlayingHero
              title={title}
              subtitle={subtitle}
              coverSeed={coverSeed}
              coverPlaying={coverPlaying}
              isDownloading={isDownloading}
              showDownloadLabel={showDownloadLabel}
              downloadProgress={downloadProgress}
              volumePercent={volumePercent}
              onVolumeChange={onVolumeChange}
            />
            <NowPlayingSeekBar
              progressRef={progressRef}
              progress={progress}
              progressPercent={progressPercent}
              positionMs={positionMs}
              durationMs={durationMs}
              disabled={disabled}
              activeWaveformPeaks={activeWaveformPeaks}
              renderWaveformBars={renderWaveformBars}
              onPointerDown={handleProgressPointerDown}
              onPointerMove={handleProgressPointerMove}
              onPointerUp={handleProgressPointerUp}
              onSeek={onSeek}
            />
            <NowPlayingControls
              isPlaying={isPlaying}
              isMusic={isMusic}
              disabled={disabled}
              isDownloading={isDownloading}
              showDownloadLabel={showDownloadLabel}
              downloadProgress={downloadProgress}
              coverPlaying={coverPlaying}
              onPlayPause={onPlayPause}
              onSeekBy={onSeekBy}
              onSkipPrevious={onSkipPrevious}
              onSkipNext={onSkipNext}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
