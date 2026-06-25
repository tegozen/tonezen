import type { MusicListTrack } from "@shared/musicList";
import {
  bulkProgressFraction,
  isBulkDownloading,
  type DownloadQueueState,
} from "@shared/downloadQueueState";

interface MusicDownloadAllButtonProps {
  tracks: MusicListTrack[];
  musicDownload: DownloadQueueState;
  onClick: () => void;
}

export function MusicDownloadAllButton({ tracks, musicDownload, onClick }: MusicDownloadAllButtonProps) {
  const total = tracks.length;
  if (total === 0) return null;

  const displayTotal = musicDownload.bulkTotal > 0 ? musicDownload.bulkTotal : total;
  const downloaded = isBulkDownloading(musicDownload)
    ? musicDownload.bulkDownloaded
    : tracks.filter((track) => track.isDownloaded).length;
  const bulkActive = isBulkDownloading(musicDownload);

  if (downloaded >= displayTotal && !bulkActive) return null;

  const progressFraction = bulkProgressFraction(musicDownload) ?? (displayTotal > 0 ? downloaded / displayTotal : 0);

  return (
    <button
      type="button"
      className="music-download-all-card"
      onClick={onClick}
    >
      <div className="music-download-all-header">
        <span className="text-sm font-semibold text-ink">
          {bulkActive ? "Остановить загрузку" : "Скачать все"}
        </span>
        <span className="text-xs text-muted">
          {`${downloaded} из ${displayTotal}`}
        </span>
      </div>
      <div className="music-download-all-progress-track">
        <div
          className="music-download-all-progress-fill"
          style={{ width: `${Math.min(progressFraction, 1) * 100}%` }}
        />
      </div>
    </button>
  );
}
