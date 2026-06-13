import type { Book, Cycle } from "@shared/types";
import { ChevronLeftIcon } from "../components/TonezenIcons";
import { CoverArt } from "../components/CoverArt";
import { DetailHeaderMenu } from "../components/DetailHeaderMenu";
import type { CycleCardState } from "../lib/cycleUtils";
import { bookAuthorLabel } from "../lib/cycleUtils";
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
    <div className="overlay-page space-y-5">
      <div className="chrome-bar flex items-center justify-between">
        <button type="button" className="icon-button h-10 w-10" onClick={onBack} aria-label={strings.back}>
          <ChevronLeftIcon className="h-5 w-5" />
        </button>
        <h1 className="truncate px-2 text-base font-semibold">{strings.cycleBooksSection}</h1>
        <DetailHeaderMenu
          showDownload={cardState.showDownload}
          showRemoveDownload={cardState.showRemoveDownload}
          isListened={cardState.isListened}
          onDownload={onDownloadCycle}
          onToggleListened={onToggleCycleListened}
          onRemoveDownloads={onRemoveCycleDownloads}
        />
      </div>
      <div className="space-y-1">
        <h2 className="text-lg font-semibold">{cycle.title}</h2>
        <p className="text-sm text-muted">{strings.cycleBooksCount(cycle.books.length)}</p>
      </div>
      <div className="space-y-3">
        {cycle.books.map((book) => (
          <button
            key={book.id}
            type="button"
            className="flex w-full items-center gap-3.5 rounded-xl py-2 text-left transition hover:bg-surface-muted/50"
            onClick={() => onBookClick(book)}
          >
            <CoverArt seed={book.id} title={book.title} className="h-[72px] w-[56px] shrink-0" />
            <div className="min-w-0 flex-1">
              <div className="truncate font-semibold">{book.title}</div>
              <div className="truncate text-sm text-muted">
                {bookAuthorLabel(book) || strings.authorPlaceholder}
              </div>
            </div>
            {downloadedBookIds.has(book.id) && <span className="chip-teal shrink-0">{strings.offline}</span>}
          </button>
        ))}
      </div>
    </div>
  );
}
