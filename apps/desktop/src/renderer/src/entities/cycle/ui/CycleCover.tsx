import type { Cycle } from "@core/types";
import { bookCoverGradient } from "@/shared/lib/coverGradient";

interface CycleCoverProps {
  cycle: Cycle;
  className?: string;
  onClick?: () => void;
}

export function CycleCover({ cycle, className = "", onClick }: CycleCoverProps) {
  const gradient = bookCoverGradient(cycle.id, true);

  return (
    <button
      type="button"
      className={`cycle-cover ${className}`}
      style={{ background: gradient }}
      onClick={onClick}
    >
      <span className="cycle-cover-orb cycle-cover-orb-tr" aria-hidden />
      <span className="cycle-cover-orb cycle-cover-orb-bl" aria-hidden />
      <div className="cycle-cover-text">
        <div className="cycle-cover-title">{cycle.title.toUpperCase()}</div>
        <div className="cycle-cover-count">{`${cycle.books.length} книг`}</div>
      </div>
    </button>
  );
}
