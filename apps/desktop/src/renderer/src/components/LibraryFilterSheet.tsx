import type { LibraryFilter } from "@shared/navigation";
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
              Поиск и фильтр
            </h3>

            <FilterChipRow
              label="Все"
              selected={filter.contentFilter === "all"}
              onClick={() => onContentFilterChange("all")}
            />
            <FilterChipRow
              label="Загруженные"
              selected={filter.contentFilter === "downloaded"}
              onClick={() => onContentFilterChange("downloaded")}
            />

            <p className="filter-sheet-section-label">Сортировка</p>
            <FilterChipRow
              label="Недавно слушали"
              selected={filter.sortOrder === "recent"}
              onClick={() => onSortOrderChange("recent")}
            />
            <FilterChipRow
              label="Название"
              selected={filter.sortOrder === "title"}
              onClick={() => onSortOrderChange("title")}
            />

            <div className="filter-sheet-actions">
              <button type="button" className="sheet-btn-secondary" onClick={onReset}>
                Сбросить
              </button>
              <button type="button" className="sheet-btn-primary" onClick={onApply}>
                Применить
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
