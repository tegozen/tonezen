import type { Track } from "@core/types";
import {
  isTrackQueued,
  progressForTrack,
  type DownloadQueueState,
} from "@core/downloads/downloadQueueState";
import { TrackDownloadedIndicator } from "@/entities/catalog";
import { TrackDownloadButton } from "./TrackDownloadButton";
import { TrackRowOverflowMenu } from "./TrackRowOverflowMenu";

interface ChapterTrackDownloadActionsProps {
  track: Track;
  listenPercent: number | null;
  isDownloaded: boolean;
  downloadQueue: DownloadQueueState;
  onToggleListened: () => void;
  onRemoveDownload: () => void;
  onDownloadTrack: () => void;
}

export function ChapterTrackDownloadActions({
  track,
  listenPercent,
  isDownloaded,
  downloadQueue,
  onToggleListened,
  onRemoveDownload,
  onDownloadTrack,
}: ChapterTrackDownloadActionsProps) {
  const isListened = listenPercent === 100;
  const downloadProgress = progressForTrack(downloadQueue, track.id);
  const isDownloading = downloadProgress != null;
  const isQueued = isTrackQueued(downloadQueue, track.id);

  return (
    <>
      {isDownloading || isQueued ? (
        <TrackDownloadButton
          downloading={isDownloading}
          progress={downloadProgress}
          onClick={onDownloadTrack}
        />
      ) : isDownloaded ? (
        <TrackDownloadedIndicator />
      ) : (
        <TrackDownloadButton onClick={onDownloadTrack} />
      )}
      <TrackRowOverflowMenu
        showDelete={isDownloaded}
        isListened={isListened}
        onToggleListened={onToggleListened}
        onDelete={onRemoveDownload}
      />
    </>
  );
}
