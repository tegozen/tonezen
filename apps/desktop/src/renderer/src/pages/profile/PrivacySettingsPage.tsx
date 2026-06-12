import { strings } from "../../i18n/strings";
import { SettingsPageLayout, SettingsInfoRow, SettingsSection } from "./SettingsPageLayout";

interface PrivacySettingsPageProps {
  onBack: () => void;
}

export function PrivacySettingsPage({ onBack }: PrivacySettingsPageProps) {
  return (
    <SettingsPageLayout title={strings.settingsPrivacyPageTitle} onBack={onBack}>
      <SettingsSection title={strings.settingsPrivacyDataSection}>
        <p className="text-sm text-muted">{strings.settingsPrivacyDataBody}</p>
      </SettingsSection>
      <SettingsSection title={strings.settingsPrivacyLockSection}>
        <div className="flex items-center justify-between gap-3">
          <SettingsInfoRow title={strings.settingsPrivacyLockSection} subtitle={strings.settingsPrivacyLockDesc} />
          <label className="flex items-center gap-2 text-sm text-muted">
            <input type="checkbox" disabled checked={false} readOnly />
            {strings.settingsComingSoon}
          </label>
        </div>
      </SettingsSection>
      <SettingsSection title={strings.settingsPrivacyPermissionsSection}>
        <p className="text-sm text-muted">{strings.settingsPrivacyLocalDataNote}</p>
      </SettingsSection>
    </SettingsPageLayout>
  );
}
