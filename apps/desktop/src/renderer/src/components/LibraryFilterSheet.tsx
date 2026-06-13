import type { LibraryFilter } from "../i18n/strings";
import { strings } from "../i18n/strings";
import { useAnimatedVisibility } from "../hooks/useAnimatedVisibility";

interface LibraryFilterSheetProps {
  visible: boolean;
  filter: LibraryFilter;
  onDismiss: () => void;
  onApply: () => void;
  onReset: () => void;
  onContentFilterChange: (value: LibraryFilter["contentFilter"]) => void;
  onSortOrderChange: (value: LibraryFilter["sortOrder"]) => void;
}

export function LibraryFilterSheet({
  visible,
  filter,
  onDismiss,
  onApply,
  onReset,
  onContentFilterChange,
  onSortOrderChange,
}: LibraryFilterSheetProps) {
  const { mounted, open } = useAnimatedVisibility(visible, 320);

  if (!mounted) return null;

  return (
    <div
      className={`filter-sheet-overlay ${open ? "filter-sheet-overlay-open" : ""}`}
      onClick={onDismiss}
    >
      <div
        className={`filter-sheet-panel ${open ? "filter-sheet-panel-open" : ""}`}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="filter-sheet-title"
      >
        <div className="filter-sheet-glass">
          <div className="sheet-handle filter-sheet-handle" />
          <div className="filter-sheet-content">
            <h3 id="filter-sheet-title" className="filter-sheet-title">
              {strings.searchFilterTitle}
            </h3>

            <FilterChipRow
              label={strings.filterAll}
              selected={filter.contentFilter === "all"}
              onClick={() => onContentFilterChange("all")}
            />
            <FilterChipRow
              label={strings.filterDownloaded}
              selected={filter.contentFilter === "downloaded"}
              onClick={() => onContentFilterChange("downloaded")}
            />

            <p className="filter-sheet-section-label">{strings.sortBy}</p>
            <FilterChipRow
              label={strings.sortRecentlyPlayed}
              selected={filter.sortOrder === "recent"}
              onClick={() => onSortOrderChange("recent")}
            />
            <FilterChipRow
              label={strings.sortTitle}
              selected={filter.sortOrder === "title"}
              onClick={() => onSortOrderChange("title")}
            />

            <div className="filter-sheet-actions">
              <button type="button" className="sheet-btn-secondary" onClick={onReset}>
                {strings.reset}
              </button>
              <button type="button" className="sheet-btn-primary" onClick={onApply}>
                {strings.apply}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function FilterChipRow({
  label,
  selected,
  onClick,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className={`filter-chip-row ${selected ? "filter-chip-row-active" : ""}`}
      onClick={onClick}
    >
      {label}
    </button>
  );
}
