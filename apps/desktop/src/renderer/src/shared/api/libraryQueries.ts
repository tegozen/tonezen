import { getTonezenApi } from "./tonezen";
import { queryKeys } from "./queryKeys";

export type LibraryBundle = {
  cycles: Awaited<ReturnType<ReturnType<typeof getTonezenApi>["db"]["getLibrarySnapshot"]>>["cycles"];
  books: Awaited<ReturnType<ReturnType<typeof getTonezenApi>["db"]["getLibrarySnapshot"]>>["books"];
  tracks: Awaited<ReturnType<ReturnType<typeof getTonezenApi>["db"]["getLibrarySnapshot"]>>["tracks"];
  storageUsedBytes: number;
  pendingCount: number;
  lastSyncAtEpochMs: number | null;
  progress: Awaited<ReturnType<ReturnType<typeof getTonezenApi>["db"]["getAllProgress"]>>;
};

export async function fetchLibraryBundle(
  reconcileLocalPaths: boolean,
): Promise<LibraryBundle> {
  const api = getTonezenApi();
  const [library, stats, sync, progress] = await Promise.all([
    api.db.getLibrarySnapshot({ reconcileLocalPaths }),
    api.download.storageStats(),
    api.sync.status(),
    api.db.getAllProgress(),
  ]);
  return {
    cycles: library.cycles,
    books: library.books,
    tracks: library.tracks,
    storageUsedBytes: stats.usedBytes,
    pendingCount: sync.pendingCount,
    lastSyncAtEpochMs: sync.lastSyncAtEpochMs,
    progress,
  };
}

export function libraryBundleQueryOptions(reconcileLocalPaths: boolean) {
  return {
    queryKey: queryKeys.libraryBundle(reconcileLocalPaths),
    queryFn: () => fetchLibraryBundle(reconcileLocalPaths),
  };
}

export async function fetchBookTracks(bookId: string) {
  return getTonezenApi().db.getTracks(bookId);
}

export function bookTracksQueryOptions(bookId: string) {
  return {
    queryKey: queryKeys.tracks(bookId),
    queryFn: () => fetchBookTracks(bookId),
    enabled: Boolean(bookId),
  };
}
