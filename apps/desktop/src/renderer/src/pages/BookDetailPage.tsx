import { useEffect, useRef } from "react";
import type { Book, Track } from "@shared/types";
import type { DownloadQueueState } from "@shared/downloadQueueState";
import { ChapterTrackRow } from "../components/ChapterTrackRow";
import { ContinueResumeMeta } from "../components/ContinueResumeMeta";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import { OverlayTopChrome } from "../components/OverlayTopChrome";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "../lib/layoutChrome";
import { buildBookTrackProgress, canContinueBookListening, resolveChapterTrackState } from "../lib/bookTrackUtils";
import { scrollActiveRowAboveBottomPadding } from "../lib/scrollActiveRow";
import { strings } from "../i18n/strings";

interface BookDetailPageProps {
  book: Book;
  tracks: Track[];
  currentTrackId: string | null;
  playbackPositionMs: number;
  downloadQueue: DownloadQueueState;
  onBack: () => void;
  onTrackClick: (track: Track) => void;
  onDownloadRequest: () => void;
  onDownloadTrack: (track: Track) => void;
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
  book,
  tracks,
  currentTrackId,
  playbackPositionMs,
  downloadQueue,
  onBack,
  onTrackClick,
  onDownloadRequest,
  onDownloadTrack,
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
    book.id,
    tracks,
    savedTrackId ? { bookId: book.id, trackId: savedTrackId, positionMs: savedPositionMs } : null,
  );
  const showContinue = continueState != null && currentTrackId == null;
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!currentTrackId) return;
    const scrollEl = scrollRef.current;
    if (!scrollEl) return;
    const rowEl = scrollEl.querySelector<HTMLElement>("[data-active-chapter-track]");
    if (!rowEl) return;

    const frame = requestAnimationFrame(() => {
      scrollActiveRowAboveBottomPadding(scrollEl, rowEl);
    });
    return () => cancelAnimationFrame(frame);
  }, [currentTrackId]);

  return (
    <div className="overlay-page">
      <div
        ref={scrollRef}
        className="scroll-under-chrome"
        style={{ paddingTop: OVERLAY_BACK_TOP_SCROLL_PX }}
      >
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
            <div key={track.id} data-active-chapter-track={isActive || undefined}>
              <ChapterTrackRow
                track={track}
                trackNumber={track.sortOrder + 1}
                isActive={isActive}
                listenProgress={listenProgress}
                listenPercent={listenPercent}
                isDownloaded={Boolean(track.localPath)}
                downloadQueue={downloadQueue}
                onClick={() => onTrackClick(track)}
                onToggleListened={() => onMarkTrackListened(track, listenPercent !== 100)}
                onRemoveDownload={() => onRemoveTrackDownload(track)}
                onDownloadTrack={() => onDownloadTrack(track)}
              />
            </div>
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
    </div>
  );
}
