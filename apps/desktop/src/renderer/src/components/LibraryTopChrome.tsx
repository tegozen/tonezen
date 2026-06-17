import { FilterIcon, SearchIcon } from "./TonezenIcons";
import { strings } from "../i18n/strings";

interface LibraryTopChromeProps {
  title: string;
  query: string;
  offlineBanner: boolean;
  showSearch: boolean;
  onQueryChange: (value: string) => void;
  onFilterClick: () => void;
}

export function LibraryTopChrome({
  title,
  query,
  offlineBanner,
  showSearch,
  onQueryChange,
  onFilterClick,
}: LibraryTopChromeProps) {
  return (
    <div className="library-chrome-wrap">
      <div className="library-chrome-shell">
        <div className="library-chrome-inner">
          {offlineBanner && <div className="library-offline-banner">{strings.noNetworkSyncPaused}</div>}
          <div className="library-chrome-title">{title}</div>
          {showSearch && (
            <div className="library-search-row">
              <label className="library-search-field">
                <SearchIcon className="h-5 w-5 shrink-0 text-muted" />
                <input
                  type="search"
                  placeholder={strings.searchLibrary}
                  value={query}
                  onChange={(e) => onQueryChange(e.target.value)}
                />
              </label>
              <button
                type="button"
                className="library-filter-btn"
                onClick={onFilterClick}
                aria-label={strings.filter}
              >
                <FilterIcon className="h-5 w-5 text-ink" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
