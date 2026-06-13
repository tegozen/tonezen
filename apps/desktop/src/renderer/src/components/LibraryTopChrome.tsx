import { FilterIcon, SearchIcon } from "./TonezenIcons";
import { strings } from "../i18n/strings";

interface LibraryTopChromeProps {
  selectedTab: number;
  query: string;
  offlineBanner: boolean;
  showSearch: boolean;
  onTabChange: (tab: number) => void;
  onQueryChange: (value: string) => void;
  onFilterClick: () => void;
}

export function LibraryTopChrome({
  selectedTab,
  query,
  offlineBanner,
  showSearch,
  onTabChange,
  onQueryChange,
  onFilterClick,
}: LibraryTopChromeProps) {
  const tabs = [strings.tabAudiobooks, strings.tabMusic];

  return (
    <div className="library-chrome-wrap">
      <div className="library-chrome-shell">
        <div className="library-chrome-inner">
          {offlineBanner && <div className="library-offline-banner">{strings.noNetworkSyncPaused}</div>}
          <div className="library-tabs">
            {tabs.map((label, index) => {
              const selected = selectedTab === index;
              return (
                <button
                  key={label}
                  type="button"
                  className="library-tab"
                  onClick={() => onTabChange(index)}
                >
                  <span className={selected ? "library-tab-label-active" : "library-tab-label"}>{label}</span>
                  <span className={`library-tab-indicator ${selected ? "library-tab-indicator-active" : ""}`} />
                </button>
              );
            })}
          </div>
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
