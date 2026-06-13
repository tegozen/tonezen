import { useState } from "react";
import {
  CheckCircleIcon,
  ChevronRightIcon,
  ProfileIcon,
  StorageIcon,
  WarningIcon,
} from "../components/TonezenIcons";
import { formatGb } from "../lib/formatTime";
import { strings } from "../i18n/strings";
import { AccountSettingsPage } from "./profile/AccountSettingsPage";
import { StorageSettingsPage } from "./profile/StorageSettingsPage";

type ProfileSettingsPage = "account" | "storage";

interface ProfilePageProps {
  displayName: string | null;
  email: string | null;
  online: boolean;
  pendingCount: number;
  storageUsedBytes: number;
  showSignOutConfirm: boolean;
  showSyncDialog: boolean;
  syncing: boolean;
  onRequestSignOut: () => void;
  onConfirmSignOut: () => void;
  onCancelSignOut: () => void;
  onSyncNow: () => void;
  onCloseSyncDialog: () => void;
  onProfileUpdated: () => void;
  onDeleteAllDownloads: () => void;
}

export function ProfilePage({
  displayName,
  email,
  online,
  pendingCount,
  storageUsedBytes,
  showSignOutConfirm,
  showSyncDialog,
  syncing,
  onRequestSignOut,
  onConfirmSignOut,
  onCancelSignOut,
  onSyncNow,
  onCloseSyncDialog,
  onProfileUpdated,
  onDeleteAllDownloads,
}: ProfilePageProps) {
  const [activePage, setActivePage] = useState<ProfileSettingsPage | null>(null);
  const [showDeleteAllConfirm, setShowDeleteAllConfirm] = useState(false);

  if (activePage === "account") {
    return (
      <AccountSettingsPage
        displayName={displayName ?? ""}
        email={email ?? ""}
        onBack={() => setActivePage(null)}
        onProfileUpdated={onProfileUpdated}
      />
    );
  }
  if (activePage === "storage") {
    return (
      <StorageSettingsPage
        usedBytes={storageUsedBytes}
        showDeleteConfirm={showDeleteAllConfirm}
        onBack={() => setActivePage(null)}
        onShowDeleteConfirm={setShowDeleteAllConfirm}
        onDeleteAll={() => {
          onDeleteAllDownloads();
          setShowDeleteAllConfirm(false);
        }}
      />
    );
  }

  return (
    <div className="space-y-5">
      <div className="chrome-bar flex items-center justify-between">
        <h1 className="text-2xl font-bold">{strings.profileTitle}</h1>
        <span className={online ? "chip-green" : "chip-amber"}>
          {online ? strings.online : strings.offline}
        </span>
      </div>
      <button
        type="button"
        className="card-hover flex w-full items-center gap-3 px-4 py-4 text-left"
        onClick={() => setActivePage("account")}
      >
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-surface-raised text-teal">
          <ProfileIcon className="h-8 w-8" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-semibold">{displayName}</div>
          {email && <div className="text-sm text-muted">{email}</div>}
        </div>
        <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted" />
      </button>
      <div className="card space-y-3">
        <div className="text-sm font-semibold text-muted">{strings.profileSyncStatusSection}</div>
        <div className="flex items-center gap-2 font-semibold">
          <CheckCircleIcon className="h-5 w-5 text-teal" />
          {strings.syncStatusAllSet}
        </div>
        <div className="text-sm text-muted">{strings.lastSyncToday}</div>
        {pendingCount > 0 && <span className="chip-amber">{strings.pending}</span>}
        <button type="button" className="btn-secondary" disabled={syncing} onClick={onSyncNow}>
          {strings.syncNow}
        </button>
      </div>
      <div className="space-y-2">
        <div className="text-sm font-semibold text-muted">{strings.profileSettingsSection}</div>
        <button
          type="button"
          className="card-hover flex w-full items-center justify-between px-4 py-3 text-left"
          onClick={() => setActivePage("storage")}
        >
          <div className="flex items-center gap-3">
            <StorageIcon className="h-6 w-6" />
            <div>
              <div>{strings.settingsStorage}</div>
              <div className="text-sm text-muted">
                {formatGb(storageUsedBytes)} · {strings.settingsStorageSubtitle}
              </div>
            </div>
          </div>
          <ChevronRightIcon className="h-5 w-5 text-muted" />
        </button>
      </div>
      <button type="button" className="btn-danger w-full" onClick={onRequestSignOut}>
        {strings.signOut}
      </button>
      <div className="card flex gap-3 border-amber/30 text-sm text-muted">
        <WarningIcon className="h-5 w-5 shrink-0 text-amber" />
        <span>{strings.musicProgressLocalWarning}</span>
      </div>
      {showSignOutConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">{strings.signOutConfirmTitle}</h2>
            <p className="mt-2 text-sm text-muted">{strings.signOutConfirmBody}</p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-secondary flex-1" onClick={onCancelSignOut}>
                {strings.cancel}
              </button>
              <button type="button" className="btn-danger flex-1" onClick={onConfirmSignOut}>
                {strings.signOut}
              </button>
            </div>
          </div>
        </div>
      )}
      {showSyncDialog && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">{strings.syncPausedTitle}</h2>
            <p className="mt-2 text-sm text-muted">{strings.syncPausedBody}</p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-primary flex-1" onClick={onCloseSyncDialog}>
                {strings.keepListening}
              </button>
              <button type="button" className="btn-secondary flex-1" onClick={onSyncNow}>
                {strings.retry}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
