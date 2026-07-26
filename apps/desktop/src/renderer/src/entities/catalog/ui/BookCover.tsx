import type { Book } from "@core/types";
import { bookCoverGradient } from "@/shared/lib/coverGradient";
import { bookAuthorLabel } from "../lib/cycleUtils";

interface BookCoverProps {
  book: Book;
  className?: string;
}

export function BookCover({ book, className = "" }: BookCoverProps) {
  const gradient = bookCoverGradient(book.id, book.contentType === "audiobook");
  const author = bookAuthorLabel(book) || "Автор не указан";

  return (
    <div className={`book-cover ${className}`} style={{ background: gradient }} aria-hidden>
      <span className="cycle-cover-orb cycle-cover-orb-tr" />
      <span className="cycle-cover-orb cycle-cover-orb-bl" />
      <div className="book-cover-text">
        <div className="book-cover-title">{book.title.toUpperCase()}</div>
        <div className="book-cover-author">{author}</div>
      </div>
    </div>
  );
}
