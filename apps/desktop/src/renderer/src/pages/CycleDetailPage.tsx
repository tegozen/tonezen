import type { AudiobookProgress, Book, Cycle, Track } from "@core/types";
import { CycleBookRow } from "../components/CycleBookRow";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import { OverlayTopChrome } from "../components/OverlayTopChrome";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "../lib/layoutChrome";
import { canContinueBookListening } from "../lib/bookTrackUtils";
import type { CycleCardState } from "../lib/cycleUtils";

interface CycleDetailPageProps {
  cycle: Cycle;
  cardState: CycleCardState;
  downloadedBookIds: Set<string>;
  tracksByBookId: Map<string, Track[]>;
  progressByBook: Map<string, AudiobookProgress>;
  onBack: () => void;
  onBookClick: (book: Book) => void;
  onDownloadCycle: () => void;
  onToggleCycleListened: () => void;
  onRemoveCycleDownloads: () => void;
}

export function CycleDetailPage({
  cycle,
  cardState,
  downloadedBookIds,
  tracksByBookId,
  progressByBook,
  onBack,
  onBookClick,
  onDownloadCycle,
  onToggleCycleListened,
  onRemoveCycleDownloads,
}: CycleDetailPageProps) {
  return (
    <div className="overlay-page">
      <div className="scroll-under-chrome" style={{ paddingTop: OVERLAY_BACK_TOP_SCROLL_PX }}>
        <div className="cycle-book-list">
          {cycle.books.map((book) => {
            const continueState = canContinueBookListening(
              book.id,
              tracksByBookId.get(book.id) ?? [],
              progressByBook.get(book.id),
            );

            return (
              <CycleBookRow
                key={book.id}
                book={book}
                downloaded={downloadedBookIds.has(book.id)}
                continueState={continueState}
                onClick={() => onBookClick(book)}
              />
            );
          })}
        </div>
      </div>
      <OverlayTopChrome
        title="Книги цикла"
        onBack={onBack}
        trailing={
          <DetailHeaderMenu
            showDownload={cardState.showDownload}
            showRemoveDownload={cardState.showRemoveDownload}
            isListened={cardState.isListened}
            onDownload={onDownloadCycle}
            onToggleListened={onToggleCycleListened}
            onRemoveDownloads={onRemoveCycleDownloads}
          />
        }
      />
    </div>
  );
}
