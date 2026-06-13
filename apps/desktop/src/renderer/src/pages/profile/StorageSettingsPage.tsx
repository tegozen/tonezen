import { strings } from "../../i18n/strings";
import { formatGb } from "../../lib/formatTime";
import { SettingsPageLayout, SettingsInfoRow, SettingsSection } from "./SettingsPageLayout";

interface StorageSettingsPageProps {
  usedBytes: number;
  showDeleteConfirm: boolean;
  onBack: () => void;
  onShowDeleteConfirm: (show: boolean) => void;
  onDeleteAll: () => void;
}

export function StorageSettingsPage({
  usedBytes,
  showDeleteConfirm,
  onBack,
  onShowDeleteConfirm,
  onDeleteAll,
}: StorageSettingsPageProps) {
  return (
    <SettingsPageLayout title={strings.settingsStoragePageTitle} onBack={onBack}>
      <SettingsSection title={strings.settingsStorageDownloadsSection}>
        <div className="font-semibold">
          {formatGb(usedBytes)} {strings.storageSavedOffline}
        </div>
        <SettingsInfoRow title={strings.settingsStorageDownloadsSection} subtitle={strings.settingsStorageDownloadsDesc} />
        <button type="button" className="btn-danger w-full" onClick={() => onShowDeleteConfirm(true)}>
          {strings.deleteAll}
        </button>
      </SettingsSection>
      {showDeleteConfirm && (
        <div className="sheet-overlay flex items-center justify-center p-5">
          <div className="modal-panel glass-panel">
            <h2 className="text-lg font-semibold">{strings.deleteAllConfirmTitle}</h2>
            <p className="mt-2 text-sm text-muted">{strings.deleteAllConfirmBody}</p>
            <div className="mt-4 flex gap-3">
              <button type="button" className="btn-secondary flex-1" onClick={() => onShowDeleteConfirm(false)}>
                {strings.cancel}
              </button>
              <button type="button" className="btn-danger flex-1" onClick={onDeleteAll}>
                {strings.deleteAll}
              </button>
            </div>
          </div>
        </div>
      )}
    </SettingsPageLayout>
  );
}
