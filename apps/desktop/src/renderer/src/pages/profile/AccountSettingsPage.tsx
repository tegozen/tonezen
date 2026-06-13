import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { AvatarCropScreen } from "../../components/AvatarCropScreen";
import { ProfileAvatar } from "../../components/ProfileAvatar";
import { strings } from "../../i18n/strings";
import { SettingsPageLayout } from "./SettingsPageLayout";
import { AccountFormSection } from "./AccountFormSection";
import { AccountLabeledField } from "./AccountLabeledField";

const ACCOUNT_OFFLINE_ERROR = "__account_offline__";
const PASSWORD_MISMATCH_ERROR = "__password_mismatch__";
const PASSWORD_TOO_SHORT_ERROR = "__password_too_short__";
const NOT_SIGNED_IN_ERROR = "__not_signed_in__";
const AVATAR_UPLOAD_FAILED_ERROR = "__avatar_upload_failed__";

interface AccountSettingsPageProps {
  displayName: string;
  email: string;
  avatarUrl: string | null;
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
    case AVATAR_UPLOAD_FAILED_ERROR:
      return strings.settingsAccountAvatarUploadFailed;
    default:
      return error;
  }
}

export function AccountSettingsPage({
  displayName,
  email,
  avatarUrl,
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
  const [cropImageUrl, setCropImageUrl] = useState<string | null>(null);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [avatarUploadError, setAvatarUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setName(displayName);
  }, [displayName]);

  useEffect(
    () => () => {
      if (cropImageUrl) URL.revokeObjectURL(cropImageUrl);
    },
    [cropImageUrl],
  );

  const openAvatarPicker = () => {
    if (avatarUploading) return;
    fileInputRef.current?.click();
  };

  const onAvatarFileSelected = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setAvatarUploadError(null);
    setCropImageUrl(URL.createObjectURL(file));
  };

  const dismissAvatarCrop = () => {
    if (cropImageUrl) URL.revokeObjectURL(cropImageUrl);
    setCropImageUrl(null);
    setAvatarUploadError(null);
  };

  const uploadAvatar = async (jpegBytes: Uint8Array) => {
    setAvatarUploading(true);
    setAvatarUploadError(null);
    try {
      await window.tonezen.session.uploadAvatar(jpegBytes);
      dismissAvatarCrop();
      onProfileUpdated();
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      setAvatarUploadError(message === ACCOUNT_OFFLINE_ERROR ? message : AVATAR_UPLOAD_FAILED_ERROR);
    } finally {
      setAvatarUploading(false);
    }
  };

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
      setNewPassword("");
      setConfirmPassword("");
      setPasswordFormNonce((value) => value + 1);
    } catch (e) {
      setPasswordError(e instanceof Error ? e.message : String(e));
    } finally {
      setPasswordSaving(false);
    }
  };

  const avatarBusy = avatarUploading;

  return (
    <>
      <SettingsPageLayout title={strings.settingsAccountPageTitle} onBack={onBack}>
        <div className="space-y-4">
          <AccountFormSection title={strings.settingsAccountProfileSection}>
            <div className="account-avatar-block">
              <button
                type="button"
                className="account-avatar-picker"
                disabled={avatarBusy}
                onClick={openAvatarPicker}
                aria-label={strings.settingsAccountAvatarChange}
              >
                <ProfileAvatar avatarUrl={avatarUrl} size={96} iconSize={40} />
                {avatarBusy && !cropImageUrl && (
                  <span className="account-avatar-uploading">
                    <span className="avatar-crop-spinner" aria-hidden="true" />
                  </span>
                )}
                <span className="account-avatar-add" aria-hidden="true">
                  +
                </span>
              </button>
              <button
                type="button"
                className="account-avatar-change-link"
                disabled={avatarBusy}
                onClick={openAvatarPicker}
              >
                {strings.settingsAccountAvatarChange}
              </button>
            </div>
            <AccountLabeledField
              label={strings.settingsAccountDisplayName}
              value={name}
              onChange={setName}
            />
            <AccountLabeledField label={strings.email} value={email} type="email" disabled />
            {resolveAccountError(profileError) && (
              <p className="text-sm text-error">{resolveAccountError(profileError)}</p>
            )}
            <button
              type="button"
              className="account-primary-btn"
              disabled={profileSaving || !name.trim() || avatarBusy}
              onClick={() => void saveProfile()}
            >
              {strings.settingsAccountSave}
            </button>
          </AccountFormSection>

          <AccountFormSection title={strings.settingsAccountPasswordSection}>
            <AccountLabeledField
              key={`new-password-${passwordFormNonce}`}
              label={strings.settingsAccountNewPassword}
              value={newPassword}
              onChange={setNewPassword}
              type="password"
              showPasswordToggle
            />
            <AccountLabeledField
              key={`confirm-password-${passwordFormNonce}`}
              label={strings.settingsAccountConfirmPassword}
              value={confirmPassword}
              onChange={setConfirmPassword}
              type="password"
              showPasswordToggle
            />
            {resolveAccountError(passwordError) && (
              <p className="text-sm text-error">{resolveAccountError(passwordError)}</p>
            )}
            <button
              type="button"
              className="account-primary-btn"
              disabled={passwordSaving || !newPassword || !confirmPassword || avatarBusy}
              onClick={() => void changePassword()}
            >
              {strings.settingsAccountChangePassword}
            </button>
          </AccountFormSection>
        </div>
      </SettingsPageLayout>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={onAvatarFileSelected}
      />

      {cropImageUrl && (
        <AvatarCropScreen
          imageUrl={cropImageUrl}
          uploading={avatarUploading}
          uploadError={resolveAccountError(avatarUploadError)}
          onBack={dismissAvatarCrop}
          onConfirm={(jpegBytes) => void uploadAvatar(jpegBytes)}
        />
      )}
    </>
  );
}
