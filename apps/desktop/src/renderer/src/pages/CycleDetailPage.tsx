import type { Book, Cycle } from "@shared/types";
import { CycleBookRow } from "../components/CycleBookRow";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import { OverlayTopChrome } from "../components/OverlayTopChrome";
import { OVERLAY_BACK_TOP_SCROLL_PX } from "../lib/layoutChrome";
import type { CycleCardState } from "../lib/cycleUtils";
import { strings } from "../i18n/strings";

interface CycleDetailPageProps {
  cycle: Cycle;
  cardState: CycleCardState;
  downloadedBookIds: Set<string>;
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
          {cycle.books.map((book) => (
            <CycleBookRow
              key={book.id}
              book={book}
              downloaded={downloadedBookIds.has(book.id)}
              onClick={() => onBookClick(book)}
            />
          ))}
        </div>
      </div>
      <OverlayTopChrome
        title={strings.cycleBooksSection}
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
