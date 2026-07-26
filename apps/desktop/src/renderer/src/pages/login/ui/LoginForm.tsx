import { EyeIcon, EyeOffIcon, LockIcon, MailIcon } from "@/shared/ui/TonezenIcons";

interface LoginFormProps {
  email: string;
  password: string;
  passwordVisible: boolean;
  error: string | null;
  canSubmit: boolean;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onTogglePasswordVisible: () => void;
  onForgotPassword: () => void;
  onNoAccount: () => void;
}

export function LoginForm({
  email,
  password,
  passwordVisible,
  error,
  canSubmit,
  onEmailChange,
  onPasswordChange,
  onTogglePasswordVisible,
  onForgotPassword,
  onNoAccount,
}: LoginFormProps) {
  return (
    <>
      <label className="auth-field">
        <MailIcon className="auth-field-icon" />
        <input
          type="email"
          placeholder="Email"
          value={email}
          onChange={(e) => onEmailChange(e.target.value)}
          autoComplete="email"
        />
      </label>
      <label className="auth-field">
        <LockIcon className="auth-field-icon" />
        <input
          type={passwordVisible ? "text" : "password"}
          placeholder="Пароль"
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          autoComplete="current-password"
        />
        <button
          type="button"
          className="auth-field-toggle"
          onClick={onTogglePasswordVisible}
          aria-label={passwordVisible ? "Скрыть" : "Показать"}
        >
          {passwordVisible ? (
            <EyeOffIcon className="h-[19px] w-[19px] text-muted" />
          ) : (
            <EyeIcon className="h-[19px] w-[19px] text-muted" />
          )}
        </button>
      </label>
      <button type="submit" className="auth-sign-in-btn" disabled={!canSubmit}>
        Войти
      </button>
      {error && <p className="error-text px-0.5 text-sm">{error}</p>}
      <div className="auth-inline-actions">
        <button type="button" onClick={onForgotPassword}>
          Забыли пароль?
        </button>
        <button type="button" onClick={onNoAccount}>
          Нет аккаунта
        </button>
      </div>
    </>
  );
}
