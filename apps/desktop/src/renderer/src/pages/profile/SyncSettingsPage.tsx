import { strings } from "../../i18n/strings";
import { SettingsPageLayout, SettingsSection } from "./SettingsPageLayout";

interface SyncSettingsPageProps {
  online: boolean;
  pendingCount: number;
  syncing: boolean;
  onBack: () => void;
  onSyncNow: () => void;
}

export function SyncSettingsPage({ online, pendingCount, syncing, onBack, onSyncNow }: SyncSettingsPageProps) {
  return (
    <SettingsPageLayout title={strings.settingsSyncPageTitle} onBack={onBack}>
      <SettingsSection title={strings.settingsSyncWhatSection}>
        <div className="space-y-4">
          <SettingsInfoInline
            title={strings.settingsSyncProgress}
            subtitle={strings.settingsSyncProgressDesc}
          />
          <SettingsInfoInline
            title={strings.settingsSyncFavorites}
            subtitle={strings.settingsSyncFavoritesDesc}
          />
          <SettingsInfoInline title={strings.settingsSyncCatalog} subtitle={strings.settingsSyncCatalogDesc} />
        </div>
      </SettingsSection>
      <SettingsSection title={strings.settingsSyncStatusSection}>
        <div className="flex flex-wrap gap-2">
          <span className={online ? "chip-green" : "chip-amber"}>
            {online ? strings.online : strings.offline}
          </span>
          {pendingCount > 0 && <span className="chip-amber">{strings.pending}</span>}
        </div>
        <p className="text-sm text-muted">{strings.lastSyncToday}</p>
        <button type="button" className="btn-primary w-full" disabled={syncing} onClick={onSyncNow}>
          {strings.syncNow}
        </button>
      </SettingsSection>
      <p className="card text-sm text-muted">{strings.settingsSyncMusicLocalNote}</p>
    </SettingsPageLayout>
  );
}

function SettingsInfoInline({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <div className="font-medium">{title}</div>
      <div className="text-sm text-muted">{subtitle}</div>
    </div>
  );
}
