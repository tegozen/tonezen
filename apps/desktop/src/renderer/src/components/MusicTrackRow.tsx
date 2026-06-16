import type { MusicListTrack } from "@shared/musicList";
import {
  isTrackQueued,
  progressForTrack,
  type DownloadQueueState,
} from "@shared/downloadQueueState";
import { TrackDownloadButton } from "./TrackDownloadButton";
import { TrackDownloadedIndicator } from "./TrackDownloadedIndicator";
import { TrackListRow } from "./TrackListRow";
import { TrackRowOverflowMenu } from "./TrackRowOverflowMenu";
import { strings } from "../i18n/strings";

interface MusicTrackRowProps {
  track: MusicListTrack;
  isActive: boolean;
  downloadQueue: DownloadQueueState;
  onClick: () => void;
  onDownloadClick: () => void;
  onDeleteClick: () => void;
}

export function MusicTrackRow({
  track,
  isActive,
  downloadQueue,
  onClick,
  onDownloadClick,
  onDeleteClick,
}: MusicTrackRowProps) {
  const downloadProgress = progressForTrack(downloadQueue, track.trackId);
  const isDownloading = downloadProgress != null;
  const isQueued = isTrackQueued(downloadQueue, track.trackId);

  return (
    <TrackListRow
      title={track.trackTitle}
      subtitle={track.artist}
      durationMs={track.durationMs}
      isActive={isActive}
      clickEnabled
      onClick={onClick}
      trailing={
        <>
          {isDownloading || isQueued ? (
            <TrackDownloadButton
              downloading={isDownloading}
              progress={downloadProgress}
              onClick={onDownloadClick}
            />
          ) : track.isDownloaded ? (
            <TrackDownloadedIndicator />
          ) : (
            <TrackDownloadButton onClick={onDownloadClick} />
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
