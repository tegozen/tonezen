import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { AvatarCropScreen } from "@/features/profile-settings";
import { SettingsPageLayout } from "@/widgets/app-shell";
import { OverlayTopChrome } from "@/widgets/top-chrome";
import { AccountFormSection } from "./AccountFormSection";
import { AccountLabeledField } from "./AccountLabeledField";
import { AccountAvatarSection } from "./AccountAvatarSection";
import { AccountPasswordSection } from "./AccountPasswordSection";

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
      return "Нужно подключение к интернету";
    case PASSWORD_MISMATCH_ERROR:
      return "Пароли не совпадают";
    case NOT_SIGNED_IN_ERROR:
      return "Войдите в аккаунт";
    case PASSWORD_TOO_SHORT_ERROR:
      return "Пароль должен быть не короче 12 символов";
    case AVATAR_UPLOAD_FAILED_ERROR:
      return "Не удалось загрузить аватар";
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
  const [currentPassword, setCurrentPassword] = useState("");
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
  const [referralCode, setReferralCode] = useState("");
  const [referralError, setReferralError] = useState<string | null>(null);
  const [referralCopied, setReferralCopied] = useState(false);
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

  useEffect(() => {
    let cancelled = false;
    window.tonezen.session.getReferralCode()
      .then((code) => {
        if (cancelled) return;
        setReferralCode(code);
        setReferralError(null);
      })
      .catch(() => {
        if (cancelled) return;
        setReferralError("Не удалось загрузить код");
      });
    return () => {
      cancelled = true;
    };
  }, []);

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
    if (!currentPassword) {
      setPasswordError("Введите текущий пароль");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError(PASSWORD_MISMATCH_ERROR);
      return;
    }
    if (newPassword.length < 12) {
      setPasswordError(PASSWORD_TOO_SHORT_ERROR);
      return;
    }
    setPasswordSaving(true);
    setPasswordError(null);
    try {
      await window.tonezen.session.changePassword(currentPassword, newPassword);
      setCurrentPassword("");
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

  const copyReferralCode = async () => {
    if (!referralCode) return;
    try {
      await navigator.clipboard.writeText(referralCode);
      setReferralCopied(true);
    } catch {
      setReferralError("Не удалось загрузить код");
    }
  };

  return (
    <>
      <SettingsPageLayout topChrome={<OverlayTopChrome title="Аккаунт" onBack={onBack} />}>
        <div className="space-y-4">
          <AccountAvatarSection
            avatarUrl={avatarUrl}
            name={name}
            email={email}
            avatarBusy={avatarBusy}
            cropImageUrl={cropImageUrl}
            profileError={resolveAccountError(profileError)}
            profileSaving={profileSaving}
            onOpenAvatarPicker={openAvatarPicker}
            onNameChange={setName}
            onSaveProfile={() => void saveProfile()}
          />

          <AccountFormSection title="Реферальный код">
            <p className="text-sm leading-relaxed text-muted">
              Поделитесь кодом, чтобы пригласить нового пользователя.
            </p>
            <AccountLabeledField label="Инвайт-код" value={referralCode || "............"} disabled />
            {referralError && <p className="text-sm text-error">{referralError}</p>}
            <button
              type="button"
              className="account-primary-btn"
              disabled={!referralCode}
              onClick={() => void copyReferralCode()}
            >
              {referralCopied ? "Скопировано" : "Скопировать"}
            </button>
          </AccountFormSection>

          <AccountPasswordSection
            currentPassword={currentPassword}
            newPassword={newPassword}
            confirmPassword={confirmPassword}
            passwordFormNonce={passwordFormNonce}
            passwordError={resolveAccountError(passwordError)}
            passwordSaving={passwordSaving}
            avatarBusy={avatarBusy}
            onCurrentPasswordChange={setCurrentPassword}
            onNewPasswordChange={setNewPassword}
            onConfirmPasswordChange={setConfirmPassword}
            onChangePassword={() => void changePassword()}
          />
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
