import { ProfilePage } from "@/pages/profile";
import { AppShellErrorBanner } from "@/app/AppShellErrorBanner";
import type { AppShellRoutesProps } from "@/app/appShellRoutesProps";

type AppShellProfileRouteProps = Pick<
  AppShellRoutesProps,
  | "sessionState"
  | "library"
  | "downloads"
  | "displayName"
  | "userEmail"
  | "avatarUrl"
  | "memberSinceEpochMs"
  | "showSignOutConfirm"
  | "setShowSignOutConfirm"
  | "showSyncDialog"
  | "setShowSyncDialog"
  | "syncing"
  | "setSyncing"
  | "triggerSync"
  | "refreshSession"
  | "handleLogout"
  | "error"
>;

export function AppShellProfileRoute({
  sessionState,
  library,
  downloads,
  displayName,
  userEmail,
  avatarUrl,
  memberSinceEpochMs,
  showSignOutConfirm,
  setShowSignOutConfirm,
  showSyncDialog,
  setShowSyncDialog,
  syncing,
  setSyncing,
  triggerSync,
  refreshSession,
  handleLogout,
  error,
}: AppShellProfileRouteProps) {
  return (
    <>
      <ProfilePage
        displayName={displayName}
        email={userEmail}
        avatarUrl={avatarUrl}
        memberSinceEpochMs={memberSinceEpochMs}
        online={sessionState === "AuthenticatedOnline"}
        pendingCount={library.pendingCount}
        lastSyncAtEpochMs={library.lastSyncAtEpochMs}
        storageUsedBytes={library.storageUsed}
        showSignOutConfirm={showSignOutConfirm}
        showSyncDialog={showSyncDialog}
        syncing={syncing}
        onRequestSignOut={() => setShowSignOutConfirm(true)}
        onConfirmSignOut={() => {
          setShowSignOutConfirm(false);
          void handleLogout();
        }}
        onCancelSignOut={() => setShowSignOutConfirm(false)}
        onSyncNow={() => {
          if (sessionState === "AuthenticatedOffline") {
            setShowSyncDialog(true);
            return;
          }
          setSyncing(true);
          void triggerSync
            .mutateAsync()
            .then(() => library.refreshLibrary())
            .finally(() => setSyncing(false));
        }}
        onCloseSyncDialog={() => setShowSyncDialog(false)}
        onProfileUpdated={() => void refreshSession()}
        onDeleteAllDownloads={() => void downloads.deleteAllDownloads()}
      />
      <AppShellErrorBanner error={error} />
    </>
  );
}
