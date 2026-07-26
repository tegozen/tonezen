import type { CSSProperties } from "react";
import { TrackSpectrumArt } from "@/shared/ui/CoverArt";

interface NowPlayingHeroProps {
  title: string;
  subtitle: string;
  coverSeed: string;
  coverPlaying: boolean;
  isDownloading: boolean;
  showDownloadLabel: boolean;
  downloadProgress: number | null;
  volumePercent: number;
  onVolumeChange: (volume: number) => void;
}

export function NowPlayingHero({
  title,
  subtitle,
  coverSeed,
  coverPlaying,
  isDownloading,
  showDownloadLabel,
  downloadProgress,
  volumePercent,
  onVolumeChange,
}: NowPlayingHeroProps) {
  return (
    <div className="now-playing-sheet-hero">
      <div className="now-playing-sheet-cover-row">
        <TrackSpectrumArt
          seed={coverSeed}
          title={title}
          isPlaying={coverPlaying}
          className="now-playing-sheet-cover h-[168px] w-[168px] rounded-[24px]"
        >
          {isDownloading ? (
            <div className="absolute inset-0 flex items-center justify-center bg-black/55 text-base font-bold text-teal">
              {showDownloadLabel && downloadProgress != null
                ? `${Math.round(downloadProgress * 100)}%`
                : "…"}
            </div>
          ) : null}
        </TrackSpectrumArt>
        <div className="now-playing-volume">
          <div className="now-playing-volume-slider-wrap">
            <input
              type="range"
              min={0}
              max={100}
              step={1}
              value={volumePercent}
              className="now-playing-volume-slider"
              style={{ "--volume-fill": `${volumePercent}%` } as CSSProperties}
              aria-label="Громкость"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={volumePercent}
              onChange={(event) => onVolumeChange(Number(event.target.value) / 100)}
            />
          </div>
          <span className="now-playing-volume-label">{volumePercent}%</span>
        </div>
      </div>
      <div className="now-playing-sheet-meta">
        <h2 className="line-clamp-2">{title}</h2>
        {subtitle ? <p className="line-clamp-2">{subtitle}</p> : null}
      </div>
    </div>
  );
}
