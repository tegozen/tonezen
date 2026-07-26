import type { ReactNode } from "react";
import type { MusicListTrack } from "@core/catalog/musicList";
import { TrackListRow } from "./TrackListRow";

interface MusicTrackRowProps {
  track: MusicListTrack;
  isActive: boolean;
  onClick: () => void;
  trailing?: ReactNode;
}

export function MusicTrackRow({ track, isActive, onClick, trailing }: MusicTrackRowProps) {
  return (
    <TrackListRow
      title={track.trackTitle}
      subtitle={track.artist}
      durationMs={track.durationMs}
      isActive={isActive}
      clickEnabled
      onClick={onClick}
      trailing={trailing}
    />
  );
}
