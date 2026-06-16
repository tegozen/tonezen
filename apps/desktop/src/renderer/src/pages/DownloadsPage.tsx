import type { DownloadQueueItem, DownloadQueueState } from "@shared/downloadQueueState";
import { libraryScrollPaddingTop } from "../lib/layoutChrome";
import { LibraryTopChrome } from "../components/LibraryTopChrome";
import { TrackDownloadButton } from "../components/TrackDownloadButton";
import { TrackListRow } from "../components/TrackListRow";
import { strings } from "../i18n/strings";

interface DownloadsPageProps {
  downloadQueue: DownloadQueueState;
  selectedTab: number;
  offlineBanner: boolean;
  onTabChange: (tab: number) => void;
  onCancelTrack: (bookId: string, trackId: string) => void;
  onCancelAll: () => void;
  onDeleteCompleted: (bookId: string, trackId: string) => void;
}

export function DownloadsPage({
  downloadQueue,
  selectedTab,
  offlineBanner,
  onTabChange,
  onCancelTrack,
  onCancelAll,
  onDeleteCompleted,
}: DownloadsPageProps) {
  const activeItems = downloadQueue.queuedItems.filter(
    (item) =>
      item.status === "QUEUED" ||
      item.status === "DOWNLOADING" ||
      item.status === "PAUSED_OFFLINE",
  );
  const completedItems = downloadQueue.completedHistory.filter(
    (item) => item.status === "COMPLETED",
  );
  const isEmpty = activeItems.length === 0 && completedItems.length === 0;

  return (
    <div className="library-page">
      <div
        className="scroll-under-chrome space-y-4"
        style={{ paddingTop: libraryScrollPaddingTop(false, offlineBanner) }}
      >
        {downloadQueue.pausedForNetwork && (
          <p className="text-sm text-muted">{strings.downloadPausedOffline}</p>
        )}
        {activeItems.length > 0 && (
          <section className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-muted">{strings.downloadsSectionActive}</h2>
              <button type="button" className="text-sm text-teal" onClick={onCancelAll}>
                {strings.musicDownloadStopAll}
              </button>
            </div>
            {activeItems.map((item) => (
              <DownloadsActiveRow
                key={`${item.bookId}:${item.trackId}`}
                item={item}
                onCancel={() => onCancelTrack(item.bookId, item.trackId)}
              />
            ))}
          </section>
        )}
        {completedItems.length > 0 && (
          <section className="space-y-2">
            <h2 className="text-sm font-semibold text-muted">{strings.downloadsSectionCompleted}</h2>
            {completedItems.map((item) => (
              <TrackListRow
                key={`${item.bookId}:${item.trackId}`}
                title={item.title}
                subtitle={item.subtitle}
                durationMs={null}
                isActive={false}
                clickEnabled={false}
                onClick={() => {}}
                trailing={
                  <button
                    type="button"
                    className="text-sm text-muted"
                    onClick={() => onDeleteCompleted(item.bookId, item.trackId)}
                  >
                    {strings.removeDownload}
                  </button>
                }
              />
            ))}
          </section>
        )}
        {isEmpty && (
          <div className="py-12 text-center">
            <p className="text-sm text-muted">{strings.downloadsEmpty}</p>
          </div>
        )}
      </div>
      <LibraryTopChrome
        selectedTab={selectedTab}
        query=""
        offlineBanner={offlineBanner}
        showSearch={false}
        onTabChange={onTabChange}
        onQueryChange={() => {}}
        onFilterClick={() => {}}
      />
    </div>
  );
}

function DownloadsActiveRow({
  item,
  onCancel,
}: {
  item: DownloadQueueItem;
  onCancel: () => void;
}) {
  const subtitle =
    item.status === "PAUSED_OFFLINE"
      ? strings.downloadPausedOffline
      : item.status === "QUEUED"
        ? strings.downloadStatusQueued
        : item.subtitle;

  return (
    <TrackListRow
      title={item.title}
      subtitle={subtitle}
      durationMs={null}
      isActive={item.status === "DOWNLOADING"}
      clickEnabled={false}
      onClick={() => {}}
      trailing={
        <TrackDownloadButton
          downloading={item.status === "DOWNLOADING"}
          progress={item.progress}
          onClick={onCancel}
        />
      }
    />
  );
}
