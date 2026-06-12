import type { Book } from "@shared/types";
import {
  DownloadsIcon,
  FilterIcon,
  MoreVerticalIcon,
  SearchIcon,
} from "../components/TonezenIcons";
import { strings } from "../i18n/strings";

interface LibraryPageProps {
  books: Book[];
  downloadedIds: Set<string>;
  query: string;
  selectedTab: number;
  offlineBanner: boolean;
  onQueryChange: (value: string) => void;
  onTabChange: (tab: number) => void;
  onBookClick: (book: Book) => void;
  onFilterClick: () => void;
}

export function LibraryPage({
  books,
  downloadedIds,
  query,
  selectedTab,
  offlineBanner,
  onQueryChange,
  onTabChange,
  onBookClick,
  onFilterClick,
}: LibraryPageProps) {
  const filtered = books.filter((book) => {
    const q = query.trim().toLowerCase();
    const matches =
      !q ||
      book.title.toLowerCase().includes(q) ||
      (book.author ?? "").toLowerCase().includes(q);
    const tabMatch =
      selectedTab === 0 ? book.contentType === "audiobook" : book.contentType === "music";
    return matches && tabMatch;
  });

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">{strings.appName}</h1>
        <button type="button" className="icon-button h-10 w-10 text-[0]" aria-label="More options">
          <MoreVerticalIcon className="h-5 w-5 text-base" />
        </button>
      </div>
      {offlineBanner && <div className="banner">{strings.noNetworkSyncPaused}</div>}
      <div className="flex border-b border-border">
        {[strings.tabAudiobooks, strings.tabMusic].map((label, index) => (
          <button
            key={label}
            type="button"
            className={`flex-1 pb-3 ${selectedTab === index ? "border-b-2 border-teal text-teal" : "text-muted"}`}
            onClick={() => onTabChange(index)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="flex gap-2">
        <div className="relative flex-1">
          <SearchIcon className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-muted" />
          <input
            className="input-field mb-0 pl-11"
            placeholder={strings.searchLibrary}
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
          />
        </div>
        <button type="button" className="icon-button h-12 w-12" onClick={onFilterClick} aria-label={strings.filter}>
          <FilterIcon className="h-5 w-5" />
        </button>
      </div>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="section-title">
            {selectedTab === 0 ? strings.tabAudiobooks : strings.tabMusic}
          </h2>
          <span className="text-sm text-teal">See all</span>
        </div>
        <div className="flex gap-4 overflow-x-auto pb-2">
          {filtered.map((book) => (
            <button
              key={book.id}
              type="button"
              className="w-36 shrink-0 text-left"
              onClick={() => onBookClick(book)}
            >
              <div className="relative mb-2 aspect-[0.78] rounded-2xl bg-surface-raised">
                {downloadedIds.has(book.id) && (
                  <span className="chip-teal absolute bottom-2 left-2 gap-1">
                    <DownloadsIcon className="h-3.5 w-3.5" />
                    {strings.offline}
                  </span>
                )}
              </div>
              <div className="font-semibold">{book.title}</div>
              <div className="text-sm text-muted">{book.author}</div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
