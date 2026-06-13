import type { Book } from "@shared/types";
import { BookCover } from "./BookCover";
import { bookAuthorLabel } from "../lib/cycleUtils";
import { strings } from "../i18n/strings";

interface CycleBookRowProps {
  book: Book;
  downloaded: boolean;
  onClick: () => void;
}

export function CycleBookRow({ book, downloaded, onClick }: CycleBookRowProps) {
  return (
    <button type="button" className="cycle-book-row" onClick={onClick}>
      <BookCover book={book} className="w-[72px] aspect-[0.78]" />
      <div className="min-w-0 flex-1">
        <div className="truncate text-base font-semibold">{book.title}</div>
        <div className="truncate text-sm text-muted">
          {bookAuthorLabel(book) || strings.authorPlaceholder}
        </div>
        {downloaded && (
          <span className="status-chip status-chip-teal mt-1">
            <span className="status-chip-dot" aria-hidden />
            {strings.offline}
          </span>
        )}
      </div>
    </button>
  );
}
