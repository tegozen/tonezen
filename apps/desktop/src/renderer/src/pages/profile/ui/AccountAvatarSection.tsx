import { ProfileAvatar } from "@/entities/user";
import { AccountFormSection } from "./AccountFormSection";
import { AccountLabeledField } from "./AccountLabeledField";

interface AccountAvatarSectionProps {
  avatarUrl: string | null;
  name: string;
  email: string;
  avatarBusy: boolean;
  cropImageUrl: string | null;
  profileError: string | null;
  profileSaving: boolean;
  onOpenAvatarPicker: () => void;
  onNameChange: (value: string) => void;
  onSaveProfile: () => void;
}

export function AccountAvatarSection({
  avatarUrl,
  name,
  email,
  avatarBusy,
  cropImageUrl,
  profileError,
  profileSaving,
  onOpenAvatarPicker,
  onNameChange,
  onSaveProfile,
}: AccountAvatarSectionProps) {
  return (
    <AccountFormSection title="Профиль">
      <div className="account-avatar-block">
        <button
          type="button"
          className="account-avatar-picker"
          disabled={avatarBusy}
          onClick={onOpenAvatarPicker}
          aria-label="Изменить фото"
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
          onClick={onOpenAvatarPicker}
        >
          Изменить фото
        </button>
      </div>
      <AccountLabeledField label="Имя" value={name} onChange={onNameChange} />
      <AccountLabeledField label="Email" value={email} type="email" disabled />
      {profileError && <p className="text-sm text-error">{profileError}</p>}
      <button
        type="button"
        className="account-primary-btn"
        disabled={profileSaving || !name.trim() || avatarBusy}
        onClick={onSaveProfile}
      >
        Сохранить
      </button>
    </AccountFormSection>
  );
}
