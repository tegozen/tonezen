import type { Book, Track } from "@shared/types";
import { ChevronLeftIcon, CloseIcon, DownloadsIcon } from "../components/TonezenIcons";
import { CoverArt } from "../components/CoverArt";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import { PlayingBars } from "../components/PlayingBars";
import { formatMs } from "../lib/formatTime";
import { strings } from "../i18n/strings";

interface BookDetailPageProps {
  book: Book;
  tracks: Track[];
  currentTrackId: string | null;
  showDownloadSheet: boolean;
  onBack: () => void;
  onTrackClick: (track: Track) => void;
  onDownloadRequest: () => void;
  onDownloadConfirm: () => void;
  onDownloadDismiss: () => void;
  onToggleBookListened: () => void;
  onRemoveBookDownloads: () => void;
  trackProgress: Map<string, number>;
  isBookListened: boolean;
  hasDownloads: boolean;
  allDownloaded: boolean;
}

export function BookDetailPage({
  book,
  tracks,
  currentTrackId,
  showDownloadSheet,
  onBack,
  onTrackClick,
  onDownloadRequest,
  onDownloadConfirm,
  onDownloadDismiss,
  onToggleBookListened,
  onRemoveBookDownloads,
  trackProgress,
  isBookListened,
  hasDownloads,
  allDownloaded,
}: BookDetailPageProps) {
  return (
    <div className="overlay-page space-y-5">
      <div className="chrome-bar flex items-center justify-between">
        <button type="button" className="icon-button h-10 w-10" onClick={onBack} aria-label={strings.back}>
          <ChevronLeftIcon className="h-5 w-5" />
        </button>
        <h1 className="truncate px-2 text-base font-semibold">{book.title}</h1>
        <DetailHeaderMenu
          showDownload={!allDownloaded}
          showRemoveDownload={hasDownloads}
          isListened={isBookListened}
          onDownload={onDownloadRequest}
          onToggleListened={onToggleBookListened}
          onRemoveDownloads={onRemoveBookDownloads}
        />
      </div>
      <div className="flex gap-4">
        <CoverArt seed={book.id} title={book.title} className="h-28 w-[88px] shrink-0" />
        <div className="min-w-0 flex-1 space-y-1">
          <h2 className="text-xl font-bold">{book.title}</h2>
          <p className="text-sm text-muted">{book.author ?? strings.authorPlaceholder}</p>
          {allDownloaded && (
            <span className="chip-teal gap-1">
              <DownloadsIcon className="h-3.5 w-3.5" />
              {strings.offline}
            </span>
          )}
        </div>
      </div>
      <div>
        <h3 className="section-title">{strings.chapters}</h3>
        <div className="mt-3 space-y-1">
          {tracks.map((track, index) => {
            const isActive = track.id === currentTrackId;
            const progress = trackProgress.get(track.id) ?? 0;
            const listenPercent = progress >= 0.95 ? 100 : progress > 0 ? Math.round(progress * 100) : null;
            return (
              <button
                key={track.id}
                type="button"
                className={`track-row ${isActive ? "track-row-active" : ""}`}
                onClick={() => onTrackClick(track)}
              >
                <div className="flex w-8 shrink-0 items-center justify-center">
                  {isActive ? (
                    <PlayingBars active />
                  ) : listenPercent != null ? (
                    <span className="text-xs font-semibold text-teal">
                      {strings.cycleListenProgress(listenPercent)}
                    </span>
                  ) : (
                    <span className={`text-sm ${isActive ? "text-amber" : "text-muted"}`}>{index + 1}</span>
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium">{track.title}</div>
                  {progress > 0 && progress < 0.95 && (
                    <div className="progress-bar mt-1.5 h-0.5">
                      <div className="progress-bar-fill bg-teal" style={{ width: `${progress * 100}%` }} />
                    </div>
                  )}
                </div>
                <span className="shrink-0 text-sm text-muted">{formatMs(track.durationMs ?? 0)}</span>
                {track.localPath && <span className="chip-teal shrink-0 px-1.5 py-0.5 text-[10px]">{strings.offline}</span>}
              </button>
            );
          })}
        </div>
      </div>
      {showDownloadSheet && (
        <div className="sheet-overlay">
          <div className="sheet-panel glass-panel">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="font-semibold">{strings.downloadConfirmTitle}</h3>
              <button type="button" className="icon-button h-9 w-9 text-[0]" onClick={onDownloadDismiss} aria-label={strings.cancel}>
                <CloseIcon className="h-5 w-5 text-base" />
              </button>
            </div>
            <p className="text-sm text-muted">{strings.downloadConfirmBody}</p>
            <button type="button" className="btn-primary mt-4 flex w-full items-center justify-center gap-2" onClick={onDownloadConfirm}>
              <DownloadsIcon className="h-5 w-5" />
              {strings.downloadOffline}
            </button>
            <button type="button" className="btn-secondary mt-3 w-full" onClick={onDownloadDismiss}>
              {strings.cancel}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
