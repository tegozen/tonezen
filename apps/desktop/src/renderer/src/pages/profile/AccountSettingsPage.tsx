import { useEffect, useState } from "react";
import { strings } from "../../i18n/strings";
import { SettingsPageLayout, SettingsSection } from "./SettingsPageLayout";

const ACCOUNT_OFFLINE_ERROR = "__account_offline__";
const PASSWORD_MISMATCH_ERROR = "__password_mismatch__";
const PASSWORD_TOO_SHORT_ERROR = "__password_too_short__";
const NOT_SIGNED_IN_ERROR = "__not_signed_in__";

interface AccountSettingsPageProps {
  displayName: string;
  email: string;
  onBack: () => void;
  onProfileUpdated: () => void;
}

function resolveAccountError(error: string | null): string | null {
  if (!error) return null;
  switch (error) {
    case ACCOUNT_OFFLINE_ERROR:
      return strings.settingsAccountOffline;
    case PASSWORD_MISMATCH_ERROR:
      return strings.settingsAccountPasswordMismatch;
    case NOT_SIGNED_IN_ERROR:
      return strings.settingsAccountNotSignedIn;
    case PASSWORD_TOO_SHORT_ERROR:
      return strings.settingsAccountPasswordTooShort;
    default:
      return error;
  }
}

export function AccountSettingsPage({
  displayName,
  email,
  onBack,
  onProfileUpdated,
}: AccountSettingsPageProps) {
  const [name, setName] = useState(displayName);
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordFormNonce, setPasswordFormNonce] = useState(0);

  useEffect(() => {
    setName(displayName);
  }, [displayName]);

  useEffect(() => {
    setNewPassword("");
    setConfirmPassword("");
  }, [passwordFormNonce]);

  const saveProfile = async () => {
    setProfileSaving(true);
    setProfileError(null);
    try {
      await window.tonezen.session.updateProfile(name.trim());
      onProfileUpdated();
    } catch (e) {
      setProfileError(e instanceof Error ? e.message : String(e));
    } finally {
      setProfileSaving(false);
    }
  };

  const changePassword = async () => {
    if (newPassword !== confirmPassword) {
      setPasswordError(PASSWORD_MISMATCH_ERROR);
      return;
    }
    if (newPassword.length < 6) {
      setPasswordError(PASSWORD_TOO_SHORT_ERROR);
      return;
    }
    setPasswordSaving(true);
    setPasswordError(null);
    try {
      await window.tonezen.session.changePassword(newPassword);
      setPasswordFormNonce((value) => value + 1);
    } catch (e) {
      setPasswordError(e instanceof Error ? e.message : String(e));
    } finally {
      setPasswordSaving(false);
    }
  };

  return (
    <SettingsPageLayout title={strings.settingsAccountPageTitle} onBack={onBack}>
      <SettingsSection title={strings.settingsAccountProfileSection}>
        <label className="block space-y-1">
          <span className="text-sm text-muted">{strings.settingsAccountDisplayName}</span>
          <input
            className="input-field w-full"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-muted">{strings.email}</span>
          <input className="input-field w-full opacity-60" value={email} disabled readOnly />
        </label>
        {resolveAccountError(profileError) && (
          <p className="text-sm text-error">{resolveAccountError(profileError)}</p>
        )}
        <button
          type="button"
          className="btn-primary w-full"
          disabled={profileSaving || !name.trim()}
          onClick={() => void saveProfile()}
        >
          {strings.settingsAccountSave}
        </button>
      </SettingsSection>
      <SettingsSection title={strings.settingsAccountPasswordSection}>
        <label className="block space-y-1">
          <span className="text-sm text-muted">{strings.settingsAccountNewPassword}</span>
          <input
            className="input-field w-full"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
        </label>
        <label className="block space-y-1">
          <span className="text-sm text-muted">{strings.settingsAccountConfirmPassword}</span>
          <input
            className="input-field w-full"
            type="password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
        </label>
        {resolveAccountError(passwordError) && (
          <p className="text-sm text-error">{resolveAccountError(passwordError)}</p>
        )}
        <button
          type="button"
          className="btn-primary w-full"
          disabled={passwordSaving || !newPassword || !confirmPassword}
          onClick={() => void changePassword()}
        >
          {strings.settingsAccountChangePassword}
        </button>
      </SettingsSection>
    </SettingsPageLayout>
  );
}
