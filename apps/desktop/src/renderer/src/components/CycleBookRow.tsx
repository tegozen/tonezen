import type { Book } from "@shared/types";
import { BookCover } from "./BookCover";
import { ContinueResumeMeta } from "./ContinueResumeMeta";
import { bookAuthorLabel } from "../lib/cycleUtils";
import type { BookContinueState } from "../lib/bookTrackUtils";
import { strings } from "../i18n/strings";

interface CycleBookRowProps {
  book: Book;
  downloaded: boolean;
  continueState?: BookContinueState | null;
  onClick: () => void;
}

export function CycleBookRow({ book, downloaded, continueState, onClick }: CycleBookRowProps) {
  return (
    <button type="button" className="cycle-book-row" onClick={onClick}>
      <BookCover book={book} className="w-[72px] aspect-[0.78]" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-base font-semibold">{book.title}</div>
        <div className="truncate text-sm text-muted">
          {bookAuthorLabel(book) || strings.authorPlaceholder}
        </div>
        {(continueState || downloaded) && (
          <div className="mt-1.5 flex flex-wrap items-end gap-2">
            {continueState && <ContinueResumeMeta state={continueState} variant="inline" />}
            {downloaded && (
              <span className="status-chip status-chip-teal">
                <span className="status-chip-dot" aria-hidden />
                {strings.offline}
              </span>
            )}
          </div>
        )}
      </div>
    </button>
  );
}
