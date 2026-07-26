import { MailIcon } from "@/shared/ui/TonezenIcons";

interface RecoveryFormProps {
  recoveryEmail: string;
  recoverySent: boolean;
  recoveryError: string | null;
  canRecover: boolean;
  onRecoveryEmailChange: (value: string) => void;
  onSubmitRecovery: () => void;
  onBackToLogin: () => void;
}

export function RecoveryForm({
  recoveryEmail,
  recoverySent,
  recoveryError,
  canRecover,
  onRecoveryEmailChange,
  onSubmitRecovery,
  onBackToLogin,
}: RecoveryFormProps) {
  return (
    <>
      <h2 className="auth-mode-title">Восстановление пароля</h2>
      <p className="auth-mode-copy">
        Введите email аккаунта. Если он зарегистрирован, мы отправим ссылку для сброса пароля.
      </p>
      <label className="auth-field">
        <MailIcon className="auth-field-icon" />
        <input
          type="email"
          placeholder="Email"
          value={recoveryEmail}
          onChange={(e) => onRecoveryEmailChange(e.target.value)}
          autoComplete="email"
        />
      </label>
      <button type="button" className="auth-sign-in-btn" disabled={!canRecover} onClick={onSubmitRecovery}>
        Отправить ссылку
      </button>
      {recoverySent && (
        <p className="success-text px-0.5 text-sm">Если аккаунт найден, письмо уже отправлено.</p>
      )}
      {recoveryError && <p className="error-text px-0.5 text-sm">{recoveryError}</p>}
      <button type="button" className="auth-text-button" onClick={onBackToLogin}>
        Назад ко входу
      </button>
    </>
  );
}
