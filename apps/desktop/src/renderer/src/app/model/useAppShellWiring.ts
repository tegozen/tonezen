import { useCallback, useState } from "react";
import type { BottomTab } from "@core/platform/navigation";
import { useToast } from "@/shared/lib/useToast";
import { useDeleteDownloadMutation, useTriggerSyncMutation } from "@/shared/api";
import { useDownloadQueue } from "@/features/downloads";
import { useTonezenSession } from "@/features/auth";
import { useIpcQueryInvalidation } from "@/app/useIpcQueryInvalidation";
import { useAppShellAuthActions } from "@/app/useAppShellAuthActions";
import { useAppShellDerivedUi } from "@/app/useAppShellDerivedUi";
import { useAppShellFeatureStack } from "@/app/useAppShellFeatureStack";

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

  const closeExpandedPlayer = useCallback(() => setShowExpandedPlayer(false), []);

  const {
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
    stopPlayback,
  } = useAppShellFeatureStack({
    sessionState,
    downloadQueue,
    closeExpandedPlayer,
    showToast,
  });

  const { handleLogin, handleLogout, openBook } = useAppShellAuthActions({
    login,
    logout,
    library,
    music,
    stopPlayback,
  });

  const {
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
  } = useAppShellDerivedUi({
    activeTab,
    setActiveTab,
    library,
    music,
    downloadQueue,
    currentTrack,
    positionMs,
    durationMs,
    refreshSession,
  });

  const deleteDownload = useDeleteDownloadMutation();
  const triggerSync = useTriggerSyncMutation();

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
