import type { Book, Track } from "@shared/types";
import { ChapterTrackRow } from "../components/ChapterTrackRow";
import { ContinueResumeMeta } from "../components/ContinueResumeMeta";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import { OverlayTopChrome } from "../components/OverlayTopChrome";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "../lib/layoutChrome";
import { buildBookTrackProgress, canContinueBookListening, resolveChapterTrackState } from "../lib/bookTrackUtils";
import { strings } from "../i18n/strings";
import { CloseIcon, DownloadsIcon } from "../components/TonezenIcons";

interface BookDetailPageProps {
  book: Book;
  tracks: Track[];
  currentTrackId: string | null;
  playbackPositionMs: number;
  showDownloadSheet: boolean;
  onBack: () => void;
  onTrackClick: (track: Track) => void;
  onDownloadRequest: () => void;
  onDownloadConfirm: () => void;
  onDownloadDismiss: () => void;
  onToggleBookListened: () => void;
  onRemoveBookDownloads: () => void;
  onMarkTrackListened: (track: Track, listened: boolean) => void;
  onRemoveTrackDownload: (track: Track) => void;
  onContinue: () => void;
  savedTrackId: string | null;
  savedPositionMs: number;
  isBookListened: boolean;
  hasDownloads: boolean;
  allDownloaded: boolean;
}

export function BookDetailPage({
  tracks,
  currentTrackId,
  playbackPositionMs,
  showDownloadSheet,
  onBack,
  onTrackClick,
  onDownloadRequest,
  onDownloadConfirm,
  onDownloadDismiss,
  onToggleBookListened,
  onRemoveBookDownloads,
  onMarkTrackListened,
  onRemoveTrackDownload,
  onContinue,
  savedTrackId,
  savedPositionMs,
  isBookListened,
  hasDownloads,
  allDownloaded,
}: BookDetailPageProps) {
  const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  const progressByTrack = buildBookTrackProgress(
    tracks,
    savedTrackId,
    savedPositionMs,
    currentTrackId,
    playbackPositionMs,
  );
  const continueState = canContinueBookListening(
    selectedBook.id,
    tracks,
    savedTrackId ? { bookId: selectedBook.id, trackId: savedTrackId, positionMs: savedPositionMs } : null,
  );
  const showContinue = continueState != null;

  return (
    <div className="overlay-page">
      <div className="scroll-under-chrome" style={{ paddingTop: OVERLAY_BACK_TOP_SCROLL_PX }}>
      {showContinue && continueState && (
        <button
          type="button"
          className="btn-primary mx-4 mb-3 flex w-[calc(100%-2rem)] justify-center"
          onClick={onContinue}
        >
          <ContinueResumeMeta state={continueState} variant="button" />
        </button>
      )}
      <div className="chapter-track-list">
        {sortedTracks.map((track) => {
          const isActive = track.id === currentTrackId;
          const { listenProgress, listenPercent } = resolveChapterTrackState(
            track,
            progressByTrack.get(track.id),
          );
          return (
            <ChapterTrackRow
              key={track.id}
              track={track}
              trackNumber={track.sortOrder + 1}
              isActive={isActive}
              listenProgress={listenProgress}
              listenPercent={listenPercent}
              isDownloaded={Boolean(track.localPath)}
              onClick={() => onTrackClick(track)}
              onToggleListened={() => onMarkTrackListened(track, listenPercent !== 100)}
              onRemoveDownload={() => onRemoveTrackDownload(track)}
            />
          );
        })}
      </div>
      </div>
      <OverlayTopChrome
        title={strings.chapters}
        onBack={onBack}
        trailing={
          <DetailHeaderMenu
            showDownload={!allDownloaded}
            showRemoveDownload={hasDownloads}
            isListened={isBookListened}
            onDownload={onDownloadRequest}
            onToggleListened={onToggleBookListened}
            onRemoveDownloads={onRemoveBookDownloads}
          />
        }
      />
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
