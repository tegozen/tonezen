import type { MusicListTrack } from "@shared/musicList";
import { TrackDownloadButton } from "./TrackDownloadButton";
import { TrackDownloadedIndicator } from "./TrackDownloadedIndicator";
import { TrackListRow } from "./TrackListRow";
import { TrackRowOverflowMenu } from "./TrackRowOverflowMenu";
import { strings } from "../i18n/strings";

interface MusicTrackRowProps {
  track: MusicListTrack;
  isActive: boolean;
  downloadProgress: number | null;
  downloadActive: boolean;
  onClick: () => void;
  onDownloadClick: () => void;
  onDeleteClick: () => void;
}

export function MusicTrackRow({
  track,
  isActive,
  downloadProgress,
  downloadActive,
  onClick,
  onDownloadClick,
  onDeleteClick,
}: MusicTrackRowProps) {
  const isDownloading = downloadProgress != null;

  return (
    <TrackListRow
      title={track.trackTitle}
      subtitle={track.artist}
      durationMs={track.durationMs}
      isActive={isActive}
      clickEnabled={!downloadActive}
      onClick={onClick}
      trailing={
        <>
          {isDownloading ? (
            <TrackDownloadButton
              downloading
              progress={downloadProgress}
              onClick={onDownloadClick}
              disabled
            />
          ) : track.isDownloaded ? (
            <TrackDownloadedIndicator />
          ) : (
            <TrackDownloadButton onClick={onDownloadClick} disabled={downloadActive} />
          )}
          {track.isDownloaded ? (
            <TrackRowOverflowMenu
              showDelete
              deleteLabel={strings.removeDownload}
              onDelete={onDeleteClick}
            />
          ) : null}
        </>
      }
    />
  );
}
