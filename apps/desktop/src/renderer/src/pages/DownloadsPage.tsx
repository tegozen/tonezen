import {
  activeDownloadItems,
  type CompletedDownloadItem,
} from "@shared/downloadsPageState";
import type { DownloadQueueItem, DownloadQueueState } from "@shared/downloadQueueState";
import { PAGE_TITLE_TOP_SCROLL_PX } from "../lib/layoutChrome";
import { TitleTopChrome } from "../components/TitleTopChrome";
import { TrackDownloadButton } from "../components/TrackDownloadButton";
import { TrackListRow } from "../components/TrackListRow";
import { strings } from "../i18n/strings";

interface DownloadsPageProps {
  downloadQueue: DownloadQueueState;
  completedItems: CompletedDownloadItem[];
  onCancelTrack: (bookId: string, trackId: string) => void;
  onCancelAll: () => void;
  onDeleteCompleted: (bookId: string, trackId: string) => void;
}

export function DownloadsPage({
  downloadQueue,
  completedItems,
  onCancelTrack,
  onCancelAll,
  onDeleteCompleted,
}: DownloadsPageProps) {
  const activeItems = activeDownloadItems(downloadQueue);
  const isEmpty = activeItems.length === 0 && completedItems.length === 0;

  return (
    <div className="library-page">
      <div
        className="scroll-under-chrome space-y-4"
        style={{ paddingTop: PAGE_TITLE_TOP_SCROLL_PX }}
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
                subtitle={item.subtitle ?? undefined}
                durationMs={item.durationMs}
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
      <TitleTopChrome title={strings.navDownloads} />
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
      subtitle={subtitle ?? undefined}
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
