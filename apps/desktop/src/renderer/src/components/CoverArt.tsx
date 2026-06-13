import type { ReactNode } from "react";
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
