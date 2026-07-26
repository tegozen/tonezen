import { AccountFormSection } from "./AccountFormSection";
import { AccountLabeledField } from "./AccountLabeledField";

interface AccountPasswordSectionProps {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
  passwordFormNonce: number;
  passwordError: string | null;
  passwordSaving: boolean;
  avatarBusy: boolean;
  onCurrentPasswordChange: (value: string) => void;
  onNewPasswordChange: (value: string) => void;
  onConfirmPasswordChange: (value: string) => void;
  onChangePassword: () => void;
}

export function AccountPasswordSection({
  currentPassword,
  newPassword,
  confirmPassword,
  passwordFormNonce,
  passwordError,
  passwordSaving,
  avatarBusy,
  onCurrentPasswordChange,
  onNewPasswordChange,
  onConfirmPasswordChange,
  onChangePassword,
}: AccountPasswordSectionProps) {
  return (
    <AccountFormSection title="Смена пароля">
      <AccountLabeledField
        key={`current-password-${passwordFormNonce}`}
        label="Текущий пароль"
        value={currentPassword}
        onChange={onCurrentPasswordChange}
        type="password"
        showPasswordToggle
      />
      <AccountLabeledField
        key={`new-password-${passwordFormNonce}`}
        label="Новый пароль"
        value={newPassword}
        onChange={onNewPasswordChange}
        type="password"
        showPasswordToggle
      />
      <AccountLabeledField
        key={`confirm-password-${passwordFormNonce}`}
        label="Подтвердите пароль"
        value={confirmPassword}
        onChange={onConfirmPasswordChange}
        type="password"
        showPasswordToggle
      />
      {passwordError && <p className="text-sm text-error">{passwordError}</p>}
      <button
        type="button"
        className="account-primary-btn"
        disabled={
          passwordSaving ||
          !currentPassword ||
          !newPassword ||
          !confirmPassword ||
          avatarBusy
        }
        onClick={onChangePassword}
      >
        Сменить пароль
      </button>
    </AccountFormSection>
  );
}
