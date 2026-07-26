import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { Book, Cycle } from "@core/types";
import { progressForTrack } from "@core/downloads/downloadQueueState";
import { findActiveMusicTrack } from "@core/playback/musicPlayback";
import type { BottomTab } from "@core/platform/navigation";
import { useToast } from "@/shared/lib/useToast";
import { getTonezenApi, useDeleteDownloadMutation, useTriggerSyncMutation } from "@/shared/api";
import { useDownloadQueue, useLibraryDownloads } from "@/features/downloads";
import { useMusicPlayback } from "@/features/music-queue";
import { useAudiobookSession, usePlayback } from "@/features/playback";
import { useTonezenSession } from "@/features/auth";
import { useLibraryController } from "@/features/library";
import { useIpcQueryInvalidation } from "@/app/useIpcQueryInvalidation";
import { isBookFullyListened } from "@/entities/catalog";

export function useAppShellWiring() {
  const session = useTonezenSession();
  const {
    sessionState,
    userEmail,
    displayName,
    avatarUrl,
    memberSinceEpochMs,
    email,
    setEmail,
    password,
    setPassword,
    error,
    login,
    verifyInviteCode,
    registerWithInvite,
    requestPasswordRecovery,
    logout,
    refreshSession,
  } = session;

  const [activeTab, setActiveTab] = useState<BottomTab>("music");
  const [showExpandedPlayer, setShowExpandedPlayer] = useState(false);
  const [showSignOutConfirm, setShowSignOutConfirm] = useState(false);
  const [showSyncDialog, setShowSyncDialog] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const { toastMessage, showToast } = useToast();
  const downloadQueue = useDownloadQueue();
  const authenticated = sessionState !== "Unauthenticated";
  useIpcQueryInvalidation(authenticated);
  const library = useLibraryController({ sessionState, downloadQueueState: downloadQueue.state });

  const closeExpandedPlayer = useCallback(() => setShowExpandedPlayer(false), []);

  const musicHandlersRef = useRef<{
    handleSkipNext: () => boolean;
    handleSkipPrevious: () => boolean;
    handleTrackEnded: () => boolean;
  }>({
    handleSkipNext: () => false,
    handleSkipPrevious: () => false,
    handleTrackEnded: () => false,
  });
  const skipHandlers = useMemo(
    () => ({
      onSkipNext: () => musicHandlersRef.current.handleSkipNext(),
      onSkipPrevious: () => musicHandlersRef.current.handleSkipPrevious(),
    }),
    [],
  );

  const {
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    playTrack,
    stopPlayback,
    onTimeUpdate,
    pauseOrResume,
    seekBy,
    seekTo,
    volume,
    setVolume,
  } = usePlayback(library.selectedBook, library.tracks, library.tracks, skipHandlers);

  const music = useMusicPlayback({
    books: library.books,
    allTracks: library.allTracks,
    musicTracks: library.musicTracks,
    setMusicTracks: library.setMusicTracks,
    setTracks: library.setTracks,
    sessionState,
    refreshLibrary: library.refreshLibrary,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    seekTo,
    currentTrack,
    positionMs,
  });

  musicHandlersRef.current = {
    handleSkipNext: music.handleSkipNext,
    handleSkipPrevious: music.handleSkipPrevious,
    handleTrackEnded: music.handleTrackEnded,
  };
  library.musicStartedInSessionRef.current = music.musicStartedInSessionRef.current;

  const downloads = useLibraryDownloads({
    sessionState,
    downloadQueue,
    currentTrack,
    selectedBook: library.selectedBook,
    setTracks: library.setTracks,
    stopPlayback,
    closeExpandedPlayer,
    refreshLibrary: library.refreshLibrary,
    showToast,
  });

  const audiobook = useAudiobookSession({
    sessionState,
    books: library.books,
    cycles: library.cycles,
    selectedBook: library.selectedBook,
    setSelectedBook: library.setSelectedBook,
    selectedCycle: library.selectedCycle,
    setSelectedCycle: library.setSelectedCycle,
    tracks: library.tracks,
    setTracks: library.setTracks,
    tracksByBookId: library.tracksByBookId,
    progressByBook: library.progressByBook,
    setProgressList: library.setProgressList,
    refreshLibrary: library.refreshLibrary,
    downloadQueue,
    playTrack,
    stopPlayback,
    pauseOrResume,
    currentTrack,
    durationMs,
    isPlaying,
    showToast,
    closeExpandedPlayer,
    music: {
      setMusicMode: music.setMusicMode,
      handleSkipNext: music.handleSkipNext,
      handleSkipPrevious: music.handleSkipPrevious,
      handleTrackEnded: music.handleTrackEnded,
    },
  });

  const openBook = useCallback(
    (book: Book, fromCycle: Cycle | null = library.selectedCycle) => {
      music.setMusicMode(false);
      return library.openBook(book, fromCycle);
    },
    [library, music],
  );

  const deleteDownload = useDeleteDownloadMutation();
  const triggerSync = useTriggerSyncMutation();

  const syncCatalog = async () => {
    try {
      await getTonezenApi().catalog.sync();
      await library.refreshLibrary({ rebuildMusic: true });
    } catch {
      await library.refreshLibrary({ rebuildMusic: true });
    }
  };

  const handleLogin = async () => {
    const ok = await login();
    if (ok) {
      await syncCatalog();
    }
  };

  const handleLogout = async () => {
    stopPlayback();
    library.setSelectedBook(null);
    library.setSelectedCycle(null);
    music.resetMusicSession();
    library.musicStartedInSessionRef.current = false;
    await logout();
  };

  const savedBookProgress = library.selectedBook
    ? library.progressByBook.get(library.selectedBook.id)
    : undefined;

  const miniTitle = currentTrack?.title ?? null;
  const activeMusicTrack = findActiveMusicTrack(
    music.musicQueue,
    music.musicQueueRef.current,
    currentTrack?.id,
  );
  const miniSubtitle = music.musicMode
    ? activeMusicTrack
      ? [activeMusicTrack.artist, activeMusicTrack.albumTitle].filter(Boolean).join(" · ") ||
        "Сейчас играет"
      : "Сейчас играет"
    : library.selectedBook?.author ?? "Сейчас играет";
  const miniDownloadProgress = currentTrack
    ? progressForTrack(downloadQueue.state, currentTrack.id)
    : null;
  const currentTrackInSelectedBook =
    library.selectedBook != null &&
    currentTrack != null &&
    library.tracks.some((track) => track.id === currentTrack.id);
  const showMiniPlayer =
    Boolean(currentTrack) &&
    (music.musicMode || currentTrackInSelectedBook || (!library.selectedBook && !library.selectedCycle));
  const handleMusicTabSelected = music.onMusicTabSelected;
  const handleTabSelect = useCallback(
    (tab: BottomTab) => {
      setActiveTab(tab);
      if (tab !== "books") library.setShowFilterSheet(false);
      if (tab === "music") handleMusicTabSelected();
      if (tab === "profile") void refreshSession();
    },
    [handleMusicTabSelected, library, refreshSession],
  );

  useEffect(() => {
    if (activeTab === "music") {
      handleMusicTabSelected();
    }
  }, [activeTab, handleMusicTabSelected]);

  const showBottomNav = !library.selectedBook && !library.selectedCycle;
  const coverSeed = currentTrack?.id ?? library.selectedBook?.id ?? "";
  const bookIsListened = isBookFullyListened(library.tracks, savedBookProgress);
  const progress = durationMs > 0 ? positionMs / durationMs : 0;

  return {
    sessionState,
    userEmail,
    displayName,
    avatarUrl,
    memberSinceEpochMs,
    email,
    setEmail,
    password,
    setPassword,
    error,
    verifyInviteCode,
    registerWithInvite,
    requestPasswordRecovery,
    refreshSession,
    activeTab,
    showExpandedPlayer,
    setShowExpandedPlayer,
    showSignOutConfirm,
    setShowSignOutConfirm,
    showSyncDialog,
    setShowSyncDialog,
    syncing,
    setSyncing,
    toastMessage,
    downloadQueue,
    library,
    music,
    downloads,
    audiobook,
    currentTrack,
    isPlaying,
    positionMs,
    durationMs,
    audioRef,
    onTimeUpdate,
    seekBy,
    seekTo,
    volume,
    setVolume,
    openBook,
    deleteDownload,
    triggerSync,
    handleLogin,
    handleLogout,
    savedBookProgress,
    miniTitle,
    activeMusicTrack,
    miniSubtitle,
    miniDownloadProgress,
    showMiniPlayer,
    handleTabSelect,
    showBottomNav,
    coverSeed,
    bookIsListened,
    progress,
  };
}

export type AppShellWiring = ReturnType<typeof useAppShellWiring>;
