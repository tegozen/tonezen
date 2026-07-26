import type { ReactNode } from "react";
import type { Track } from "@core/types";
import { TrackListRow } from "./TrackListRow";

interface ChapterTrackRowProps {
  track: Track;
  trackNumber: number;
  isActive: boolean;
  listenProgress: number | null;
  listenPercent: number | null;
  onClick: () => void;
  trailing?: ReactNode;
}

export function ChapterTrackRow({
  track,
  trackNumber,
  isActive,
  listenProgress,
  listenPercent,
  onClick,
  trailing,
}: ChapterTrackRowProps) {
  return (
    <TrackListRow
      title={track.title}
      durationMs={track.durationMs}
      isActive={isActive}
      listenProgress={listenProgress}
      onClick={onClick}
      leading={
        <div className="flex items-center gap-2">
          {listenPercent != null ? (
            <span className="text-xs font-semibold text-teal">
              {`${listenPercent}%`}
            </span>
          ) : null}
          <span
            className={`text-sm ${
              isActive ? "text-amber" : listenPercent != null ? "text-teal" : "text-muted"
            }`}
          >
            {trackNumber}
          </span>
        </div>
      }
      trailing={trailing}
    />
  );
}
