export type BottomTab = "music" | "books" | "downloads" | "profile";

export const BOTTOM_NAV_TABS: readonly BottomTab[] = ["music", "books", "downloads", "profile"];

export type ContentFilter = "all" | "downloaded";
export type SortOrder = "recent" | "title";

export interface LibraryFilter {
  contentFilter: ContentFilter;
  sortOrder: SortOrder;
}
