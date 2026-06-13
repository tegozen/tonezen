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

export function TrackCoverArt({
  seed,
  title,
  className = "",
  children,
}: {
  seed: string;
  title?: string;
  className?: string;
  children?: ReactNode;
}) {
  return (
    <div
      className={`relative overflow-hidden rounded-3xl ${className}`}
      style={{ background: trackCoverGradient(seed) }}
      aria-label={title}
    >
      {children}
    </div>
  );
}
