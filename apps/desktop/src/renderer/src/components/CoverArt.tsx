import type { ReactNode } from "react";
import { useMemo } from "react";
import { buildSpectrumBars } from "@shared/spectrumBars";
import { bookCoverGradient, trackCoverGradient } from "../lib/coverGradient";

interface CoverArtProps {
  seed: string;
  title?: string;
  audiobook?: boolean;
  className?: string;
  children?: ReactNode;
}

export function CoverArt({ seed, title, audiobook = true, className = "", children }: CoverArtProps) {
  const gradient = audiobook ? bookCoverGradient(seed, true) : trackCoverGradient(seed);
  return (
    <div
      className={`relative overflow-hidden rounded-2xl ${className}`}
      style={{ background: gradient }}
      aria-label={title}
    >
      {children}
    </div>
  );
}

function coverInitials(title?: string): string {
  if (!title?.trim()) return "♪";
  const parts = title.trim().split(/\s+/).slice(0, 2);
  const initials = parts.map((part) => part[0]?.toUpperCase() ?? "").join("");
  return initials || "♪";
}

export function TrackCoverArt({
  seed,
  title,
  className = "",
  isPlaying = false,
  showInitial = false,
  children,
}: {
  seed: string;
  title?: string;
  className?: string;
  isPlaying?: boolean;
  showInitial?: boolean;
  children?: ReactNode;
}) {
  const initials = showInitial ? coverInitials(title) : null;

  return (
    <div
      className={`track-cover-art relative overflow-hidden rounded-3xl ${isPlaying ? "track-cover-art-playing" : "track-cover-art-idle"} ${className}`}
      style={{ background: trackCoverGradient(seed) }}
      aria-label={title}
    >
      {initials ? (
        <span
          className={`track-cover-art-initial ${isPlaying ? "track-cover-art-initial-playing" : ""}`}
          aria-hidden
        >
          {initials}
        </span>
      ) : null}
      {children}
    </div>
  );
}

export function TrackSpectrumArt({
  seed,
  title,
  className = "",
  isPlaying = false,
  children,
}: {
  seed: string;
  title?: string;
  className?: string;
  isPlaying?: boolean;
  children?: ReactNode;
}) {
  const bars = useMemo(() => buildSpectrumBars(seed), [seed]);

  return (
    <div
      className={`track-spectrum-art relative overflow-hidden rounded-3xl ${isPlaying ? "track-cover-art-playing track-spectrum-art-playing" : "track-cover-art-idle track-spectrum-art-idle"} ${className}`}
      style={{ background: trackCoverGradient(seed) }}
      aria-label={title}
    >
      <div className="track-spectrum-glow" aria-hidden />
      <div className="track-spectrum-bars" aria-hidden>
        {bars.map((bar, index) => (
          <span
            key={`${index}-${bar.level}-${bar.delayStep}`}
            className={`track-spectrum-bar track-spectrum-bar-level-${bar.level} track-spectrum-bar-delay-${bar.delayStep}`}
          />
        ))}
      </div>
      {children}
    </div>
  );
}
