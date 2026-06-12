import { ChevronRightIcon } from "../../components/TonezenIcons";
import { strings } from "../../i18n/strings";
import { SettingsPageLayout, SettingsInfoRow, SettingsSection } from "./SettingsPageLayout";

interface StorageSettingsPageProps {
  usedBytes: number;
  onBack: () => void;
  onOpenDownloads: () => void;
}

function formatGb(bytes: number): string {
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

export function StorageSettingsPage({ usedBytes, onBack, onOpenDownloads }: StorageSettingsPageProps) {
  return (
    <SettingsPageLayout title={strings.settingsStoragePageTitle} onBack={onBack}>
      <SettingsSection title={strings.settingsStorageDownloadsSection}>
        <div className="font-semibold">
          {formatGb(usedBytes)} {strings.storageSavedOffline}
        </div>
        <SettingsInfoRow title={strings.settingsStorageDownloadsSection} subtitle={strings.settingsStorageDownloadsDesc} />
        <button type="button" className="card-hover flex w-full items-center justify-between px-4 py-3" onClick={onOpenDownloads}>
          <span className="font-medium">{strings.settingsStorageManageDownloads}</span>
          <ChevronRightIcon className="h-5 w-5 text-muted" />
        </button>
      </SettingsSection>
      <SettingsSection title={strings.settingsStorageCacheSection}>
        <SettingsInfoRow title={strings.settingsStorageCacheSection} subtitle={strings.settingsStorageCacheDesc} />
      </SettingsSection>
      <SettingsSection title={strings.settingsStorageDeviceSection}>
        <SettingsInfoRow title={strings.settingsStorageDeviceSection} subtitle={strings.settingsStorageDeviceDesc} />
      </SettingsSection>
    </SettingsPageLayout>
  );
}
