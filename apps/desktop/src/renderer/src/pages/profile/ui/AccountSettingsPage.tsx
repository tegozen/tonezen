import { AvatarCropScreen } from "@/features/profile-settings";
import { SettingsPageLayout } from "@/widgets/app-shell";
import { OverlayTopChrome } from "@/widgets/top-chrome";
import { AccountFormSection } from "./AccountFormSection";
import { AccountLabeledField } from "./AccountLabeledField";
import { AccountAvatarSection } from "./AccountAvatarSection";
import { AccountPasswordSection } from "./AccountPasswordSection";
import { resolveAccountError, useAccountSettingsState } from "./useAccountSettingsState";

interface AccountSettingsPageProps {
  displayName: string;
  email: string;
  avatarUrl: string | null;
  onBack: () => void;
  onProfileUpdated: () => void;
}

export function AccountSettingsPage({
  displayName,
  email,
  avatarUrl,
  onBack,
  onProfileUpdated,
}: AccountSettingsPageProps) {
  const state = useAccountSettingsState(displayName, onProfileUpdated);

  return (
    <>
      <SettingsPageLayout topChrome={<OverlayTopChrome title="Аккаунт" onBack={onBack} />}>
        <div className="space-y-4">
          <AccountAvatarSection
            avatarUrl={avatarUrl}
            name={state.name}
            email={email}
            avatarBusy={state.avatarBusy}
            cropImageUrl={state.cropImageUrl}
            profileError={resolveAccountError(state.profileError)}
            profileSaving={state.profileSaving}
            onOpenAvatarPicker={state.openAvatarPicker}
            onNameChange={state.setName}
            onSaveProfile={() => void state.saveProfile()}
          />

          <AccountFormSection title="Реферальный код">
            <p className="text-sm leading-relaxed text-muted">
              Поделитесь кодом, чтобы пригласить нового пользователя.
            </p>
            <AccountLabeledField
              label="Инвайт-код"
              value={state.referralCode || "............"}
              disabled
            />
            {state.referralError && <p className="text-sm text-error">{state.referralError}</p>}
            <button
              type="button"
              className="account-primary-btn"
              disabled={!state.referralCode}
              onClick={() => void state.copyReferralCode()}
            >
              {state.referralCopied ? "Скопировано" : "Скопировать"}
            </button>
          </AccountFormSection>

          <AccountPasswordSection
            currentPassword={state.currentPassword}
            newPassword={state.newPassword}
            confirmPassword={state.confirmPassword}
            passwordFormNonce={state.passwordFormNonce}
            passwordError={resolveAccountError(state.passwordError)}
            passwordSaving={state.passwordSaving}
            avatarBusy={state.avatarBusy}
            onCurrentPasswordChange={state.setCurrentPassword}
            onNewPasswordChange={state.setNewPassword}
            onConfirmPasswordChange={state.setConfirmPassword}
            onChangePassword={() => void state.changePassword()}
          />
        </div>
      </SettingsPageLayout>

      <input
        ref={state.fileInputRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={state.onAvatarFileSelected}
      />

      {state.cropImageUrl && (
        <AvatarCropScreen
          imageUrl={state.cropImageUrl}
          uploading={state.avatarUploading}
          uploadError={resolveAccountError(state.avatarUploadError)}
          onBack={state.dismissAvatarCrop}
          onConfirm={(jpegBytes) => void state.uploadAvatar(jpegBytes)}
        />
      )}
    </>
  );
}
