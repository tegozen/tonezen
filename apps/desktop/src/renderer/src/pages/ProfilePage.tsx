import type { ReactNode } from "react";
import {
  CheckCircleIcon,
  ChevronRightIcon,
  LockIcon,
  MoreVerticalIcon,
  ProfileIcon,
  StorageIcon,
  SyncIcon,
  WarningIcon,
} from "../components/TonezenIcons";
import { strings } from "../i18n/strings";

interface ProfilePageProps {
  displayName: string | null;
  email: string | null;
  online: boolean;
  pendingCount: number;
  storageUsedBytes: number;
  showMenu: boolean;
  showSignOutConfirm: boolean;
  showSyncDialog: boolean;
  syncing: boolean;
  onToggleMenu: () => void;
  onCloseMenu: () => void;
  onRequestSignOut: () => void;
  onConfirmSignOut: () => void;
  onCancelSignOut: () => void;
  onSyncNow: () => void;
  onCloseSyncDialog: () => void;
}

function formatGb(bytes: number): string {
  return `${(bytes / (1024 ** 3)).toFixed(1)} GB`;
}

export function ProfilePage({
  displayName,
  email,
  online,
  pendingCount,
  storageUsedBytes,
  showMenu,
  showSignOutConfirm,
  showSyncDialog,
  syncing,
  onToggleMenu,
  onCloseMenu,
  onRequestSignOut,
  onConfirmSignOut,
  onCancelSignOut,
  onSyncNow,
  onCloseSyncDialog,
}: ProfilePageProps) {
  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">{strings.profileTitle}</h1>
        <div className="flex items-center gap-2">
          <span className={online ? "chip-green" : "chip-amber"}>
            {online ? strings.online : strings.offline}
          </span>
          <div className="relative">
            <button type="button" className="icon-button h-10 w-10 text-[0]" onClick={onToggleMenu} aria-label="More options">
              <MoreVerticalIcon className="h-5 w-5 text-base" />
            </button>
            {showMenu && (
              <div className="absolute right-0 top-11 z-30 min-w-36 rounded-xl border border-border bg-surface-raised p-2 shadow-lg">
                <button
                  type="button"
                  className="block w-full rounded-lg px-3 py-2 text-left hover:bg-surface-muted"
                  onClick={() => {
                    onCloseMenu();
                    onRequestSignOut();
                  }}
                >
                  {strings.signOut}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-3">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-surface-raised text-teal">
          <ProfileIcon className="h-8 w-8" />
        </div>
        <div>
          <div className="font-semibold">{displayName}</div>
          {email && <div className="text-sm text-muted">{email}</div>}
        </div>
      </div>
      <div className="card space-y-3">
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
        <SettingsRow icon={<ProfileIcon className="h-6 w-6" />} title={strings.settingsAccount} subtitle={email ?? ""} />
        <SettingsRow icon={<SyncIcon className="h-6 w-6" />} title={strings.settingsSync} subtitle={strings.syncNow} />
        <SettingsRow icon={<StorageIcon className="h-6 w-6" />} title={strings.settingsStorage} subtitle={formatGb(storageUsedBytes)} />
        <SettingsRow icon={<LockIcon className="h-6 w-6" />} title={strings.settingsPrivacy} subtitle="" />
      </div>
      <div className="card flex gap-3 border-amber/30 text-sm text-muted">
        <WarningIcon className="h-5 w-5 shrink-0 text-amber" />
        <span>{strings.musicProgressLocalWarning}</span>
      </div>
      {showSignOutConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel">
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
          <div className="modal-panel">
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

function SettingsRow({ icon, title, subtitle }: { icon: ReactNode; title: string; subtitle: string }) {
  return (
    <div className="card flex items-center justify-between px-4 py-3">
      <div className="flex items-center gap-3">
        <div className="text-ink">{icon}</div>
        <div>
          <div>{title}</div>
          {subtitle && <div className="text-sm text-muted">{subtitle}</div>}
        </div>
      </div>
      <ChevronRightIcon className="h-5 w-5 text-muted" />
    </div>
  );
}
