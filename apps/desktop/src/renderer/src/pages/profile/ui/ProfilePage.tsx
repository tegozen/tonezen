import { useState } from "react";
import { PAGE_TITLE_TOP_SCROLL_PX } from "@/shared/lib/layoutChrome";
import { formatMemberSinceDate, formatLastSyncLabel } from "@/entities/user";
import { ProfileAvatar } from "@/entities/user";
import { TitleTopChrome } from "@/widgets/top-chrome";
import {
  CheckCircleIcon,
  ChevronRightIcon,
  StorageIcon,
} from "@/shared/ui/TonezenIcons";
import { AccountSettingsPage } from "@/pages/account-settings";
import { StorageSettingsPage } from "@/pages/storage-settings";

type ProfileSettingsPage = "account" | "storage";

interface ProfilePageProps {
  displayName: string | null;
  email: string | null;
  avatarUrl: string | null;
  memberSinceEpochMs: number | null;
  online: boolean;
  pendingCount: number;
  lastSyncAtEpochMs: number | null;
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
  avatarUrl,
  memberSinceEpochMs,
  online,
  pendingCount,
  lastSyncAtEpochMs,
  storageUsedBytes,
  showSignOutConfirm,
  showSyncDialog,
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
        avatarUrl={avatarUrl}
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

  const memberSinceLabel = formatMemberSinceDate(memberSinceEpochMs);
  const lastSyncLabel = formatLastSyncLabel(lastSyncAtEpochMs, {
    todayAt: "Последняя синхронизация: сегодня, {time}",
    never: "Синхронизация ещё не выполнялась",
  });

  return (
    <div className="profile-page">
      <div className="scroll-under-chrome space-y-4" style={{ paddingTop: PAGE_TITLE_TOP_SCROLL_PX }}>
        <button type="button" className="profile-user-card" onClick={() => setActivePage("account")}>
          <ProfileAvatar avatarUrl={avatarUrl} />
          <div className="profile-user-meta">
            <div className="font-semibold">{displayName}</div>
            {email && <div className="profile-user-email">{email}</div>}
            {memberSinceLabel && (
              <div className="profile-user-member-since">
                {`Участник с ${memberSinceLabel}`}
              </div>
            )}
          </div>
        </button>

        <div>
          <div className="profile-section-label">Статус синхронизации</div>
          <div className="profile-sync-card">
            <CheckCircleIcon className="mt-0.5 h-[22px] w-[22px] shrink-0 text-teal" />
            <div className="min-w-0 flex-1 space-y-1">
              <div className="font-semibold">Всё в порядке</div>
              <div className="text-sm text-muted">{lastSyncLabel}</div>
              {pendingCount > 0 && <span className="chip-amber">Ожидает</span>}
            </div>
          </div>
        </div>

        <div className="space-y-3">
          <div className="profile-section-label">Настройки</div>
          <div className="profile-settings-group">
            <button type="button" className="profile-settings-row" onClick={() => setActivePage("storage")}>
              <div className="flex min-w-0 items-center gap-3">
                <StorageIcon className="h-6 w-6 shrink-0" />
                <div className="min-w-0">
                  <div className="font-medium">Хранилище</div>
                  <div className="text-sm text-muted">Офлайн-файлы на устройстве</div>
                </div>
              </div>
              <ChevronRightIcon className="h-5 w-5 shrink-0 text-muted" />
            </button>
          </div>
          <button type="button" className="profile-sign-out-card" onClick={onRequestSignOut}>
            Выйти
          </button>
        </div>
      </div>
      <TitleTopChrome
        title="Профиль"
        trailing={
          <span className={online ? "chip-green" : "chip-amber"}>
            {online ? "Онлайн" : "Офлайн"}
          </span>
        }
      />
      {showSignOutConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">Выйти из аккаунта?</h2>
            <p className="mt-2 text-sm text-muted">
              Офлайн-загрузки останутся на устройстве. Прогресс аудиокниг синхронизируется снова после входа онлайн.
            </p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-secondary flex-1" onClick={onCancelSignOut}>
                Отмена
              </button>
              <button type="button" className="btn-danger flex-1" onClick={onConfirmSignOut}>
                Выйти
              </button>
            </div>
          </div>
        </div>
      )}
      {showSyncDialog && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">Синхронизация приостановлена</h2>
            <p className="mt-2 text-sm text-muted">
              Вы офлайн. Прогресс аудиокниг синхронизируется, когда появится сеть.
            </p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-primary flex-1" onClick={onCloseSyncDialog}>
                Продолжить слушать
              </button>
              <button type="button" className="btn-secondary flex-1" onClick={onSyncNow}>
                Повторить
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
