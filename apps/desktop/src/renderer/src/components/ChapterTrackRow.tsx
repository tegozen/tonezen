import type { Track } from "@shared/types";
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

interface ChapterTrackRowProps {
  track: Track;
  trackNumber: number;
  isActive: boolean;
  listenProgress: number | null;
  listenPercent: number | null;
  isDownloaded: boolean;
  downloadQueue: DownloadQueueState;
  onClick: () => void;
  onToggleListened: () => void;
  onRemoveDownload: () => void;
  onDownloadTrack: () => void;
}

export function ChapterTrackRow({
  track,
  trackNumber,
  isActive,
  listenProgress,
  listenPercent,
  isDownloaded,
  downloadQueue,
  onClick,
  onToggleListened,
  onRemoveDownload,
  onDownloadTrack,
}: ChapterTrackRowProps) {
  const isListened = listenPercent === 100;
  const downloadProgress = progressForTrack(downloadQueue, track.id);
  const isDownloading = downloadProgress != null;
  const isQueued = isTrackQueued(downloadQueue, track.id);

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
              {strings.cycleListenProgress(listenPercent)}
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
      trailing={
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
      }
    />
  );
}
