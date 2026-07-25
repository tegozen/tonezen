import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { AudiobookProgress, Book, Cycle, SessionState, Track } from "@core/types";
import {
  buildMusicTrackListForCatalogUpdate,
  visibleMusicTrackList,
  type MusicListTrack,
} from "@core/catalog/musicList";
import { completedDownloadItems } from "@core/downloads/downloadsPageState";
import type { DownloadQueueState } from "@core/downloads/downloadQueueState";
import type { LibraryFilter } from "@core/platform/navigation";
import {
  buildTracksByBookId,
  computeCycleCardState,
  filterAndSortCycles,
  isBookFullyDownloaded,
} from "@/entities/cycle";
import {
  getTonezenApi,
  libraryBundleQueryOptions,
  queryKeys,
  type LibraryBundle,
} from "@/shared/api";
import { useIpcQueryInvalidation } from "@/app/providers/useIpcQueryInvalidation";

export type RefreshLibraryOptions = { rebuildMusic?: boolean; reconcileLocalPaths?: boolean };

export const defaultLibraryFilter: LibraryFilter = { contentFilter: "all", sortOrder: "recent" };

interface UseLibraryControllerOptions {
  sessionState: SessionState;
  downloadQueueState: DownloadQueueState;
}

export function useLibraryController({ sessionState, downloadQueueState }: UseLibraryControllerOptions) {
  const api = getTonezenApi();
  const queryClient = useQueryClient();

  const [selectedCycle, setSelectedCycle] = useState<Cycle | null>(null);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [tracks, setTracks] = useState<Track[]>([]);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<LibraryFilter>(defaultLibraryFilter);
  const [showFilterSheet, setShowFilterSheet] = useState(false);
  const [musicTracks, setMusicTracks] = useState<MusicListTrack[]>([]);
  const [reconcileLocalPaths, setReconcileLocalPaths] = useState(false);
  const [progressOverride, setProgressOverride] = useState<AudiobookProgress[] | null>(null);

  const musicStartedInSessionRef = useRef(false);
  const authenticated = sessionState !== "Unauthenticated";

  useIpcQueryInvalidation(authenticated);

  const libraryQuery = useQuery({
    ...libraryBundleQueryOptions(reconcileLocalPaths),
    enabled: authenticated,
  });

  useEffect(() => {
    if (!authenticated) {
      setReconcileLocalPaths(false);
      return;
    }
    // Cold start: first snapshot without reconcile, then enable path reconcile.
    if (libraryQuery.isSuccess && !reconcileLocalPaths) {
      setReconcileLocalPaths(true);
    }
  }, [authenticated, libraryQuery.isSuccess, reconcileLocalPaths]);

  useEffect(() => {
    if (sessionState !== "AuthenticatedOnline") return;
    void api.catalog.sync().catch(() => {});
  }, [sessionState, api]);

  const bundle: LibraryBundle | undefined = libraryQuery.data;
  const cycles = bundle?.cycles ?? [];
  const books = bundle?.books ?? [];
  const allTracks = bundle?.tracks ?? [];
  const storageUsed = bundle?.storageUsedBytes ?? 0;
  const pendingCount = bundle?.pendingCount ?? 0;
  const lastSyncAtEpochMs = bundle?.lastSyncAtEpochMs ?? null;
  const progressList = progressOverride ?? bundle?.progress ?? [];

  useEffect(() => {
    setProgressOverride(null);
  }, [libraryQuery.dataUpdatedAt]);

  useEffect(() => {
    if (!bundle) return;
    setMusicTracks((current) =>
      buildMusicTrackListForCatalogUpdate(
        current,
        bundle.books,
        bundle.tracks,
        musicStartedInSessionRef.current,
      ),
    );
  }, [bundle]);

  const setProgressList = useCallback(
    (value: AudiobookProgress[] | ((prev: AudiobookProgress[]) => AudiobookProgress[])) => {
      setProgressOverride((prev) => {
        const base = prev ?? bundle?.progress ?? [];
        return typeof value === "function" ? value(base) : value;
      });
    },
    [bundle?.progress],
  );

  const refreshLibrary = useCallback(
    async (options?: RefreshLibraryOptions) => {
      if (options?.reconcileLocalPaths != null) {
        setReconcileLocalPaths(options.reconcileLocalPaths);
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.libraryBundleAll });
      if (options?.rebuildMusic === false) return;
      const data = queryClient.getQueryData<LibraryBundle>(
        queryKeys.libraryBundle(options?.reconcileLocalPaths ?? reconcileLocalPaths),
      );
      if (data) {
        setMusicTracks((current) =>
          buildMusicTrackListForCatalogUpdate(
            current,
            data.books,
            data.tracks,
            musicStartedInSessionRef.current,
          ),
        );
      }
    },
    [queryClient, reconcileLocalPaths],
  );

  const tracksByBookId = useMemo(() => buildTracksByBookId(allTracks), [allTracks]);

  const downloadedBookIds = useMemo(() => {
    const ids = new Set<string>();
    for (const book of books) {
      if (isBookFullyDownloaded(book.id, tracksByBookId)) ids.add(book.id);
    }
    return ids;
  }, [books, tracksByBookId]);

  const progressByBook = useMemo(
    () => new Map(progressList.map((p) => [p.bookId, p])),
    [progressList],
  );

  const cycleCardStateById = useMemo(() => {
    const map: Record<string, ReturnType<typeof computeCycleCardState>> = {};
    for (const cycle of cycles) {
      map[cycle.id] = computeCycleCardState(cycle, downloadedBookIds, tracksByBookId, progressByBook);
    }
    return map;
  }, [cycles, downloadedBookIds, tracksByBookId, progressByBook]);

  const filteredCycles = useMemo(
    () => filterAndSortCycles(cycles, query, filter, downloadedBookIds, progressByBook),
    [cycles, query, filter, downloadedBookIds, progressByBook],
  );

  const completedDownloads = useMemo(
    () => completedDownloadItems(downloadQueueState, allTracks, books),
    [allTracks, books, downloadQueueState],
  );

  const visibleMusicTracks = useMemo(
    () => visibleMusicTrackList(musicTracks, sessionState === "AuthenticatedOnline"),
    [musicTracks, sessionState],
  );

  const openBook = useCallback(
    async (book: Book, fromCycle: Cycle | null = null) => {
      setSelectedBook(book);
      const bookTracks = await api.db.getTracks(book.id);
      setTracks(bookTracks);
      if (fromCycle) setSelectedCycle(fromCycle);
    },
    [api],
  );

  const resetFilter = useCallback(() => setFilter(defaultLibraryFilter), []);

  const isLoading = authenticated && (libraryQuery.isLoading || libraryQuery.isFetching) && !bundle;

  return {
    cycles,
    books,
    allTracks,
    tracks,
    setTracks,
    selectedCycle,
    setSelectedCycle,
    selectedBook,
    setSelectedBook,
    query,
    setQuery,
    filter,
    setFilter,
    resetFilter,
    showFilterSheet,
    setShowFilterSheet,
    isLoading,
    setIsLoading: () => {
      /* loading is driven by React Query */
    },
    storageUsed,
    pendingCount,
    lastSyncAtEpochMs,
    progressList,
    setProgressList,
    musicTracks,
    setMusicTracks,
    visibleMusicTracks,
    musicStartedInSessionRef,
    refreshLibrary,
    tracksByBookId,
    downloadedBookIds,
    progressByBook,
    cycleCardStateById,
    filteredCycles,
    completedDownloads,
    openBook,
  };
}

export type LibraryController = ReturnType<typeof useLibraryController>;
