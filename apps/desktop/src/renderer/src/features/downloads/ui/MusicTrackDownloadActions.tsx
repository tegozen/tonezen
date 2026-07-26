import type { MusicListTrack } from "@core/catalog/musicList";
import {
  isTrackQueued,
  progressForTrack,
  type DownloadQueueState,
} from "@core/downloads/downloadQueueState";
import { TrackDownloadedIndicator } from "@/entities/catalog";
import { TrackDownloadButton } from "./TrackDownloadButton";
import { TrackRowOverflowMenu } from "./TrackRowOverflowMenu";

interface MusicTrackDownloadActionsProps {
  track: MusicListTrack;
  downloadQueue: DownloadQueueState;
  onDownloadClick: () => void;
  onDeleteClick: () => void;
}

export function MusicTrackDownloadActions({
  track,
  downloadQueue,
  onDownloadClick,
  onDeleteClick,
}: MusicTrackDownloadActionsProps) {
  const downloadProgress = progressForTrack(downloadQueue, track.trackId);
  const isDownloading = downloadProgress != null;
  const isQueued = isTrackQueued(downloadQueue, track.trackId);

  return (
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
          deleteLabel="Удалить загрузку"
          onDelete={onDeleteClick}
        />
      ) : null}
    </>
  );
}
