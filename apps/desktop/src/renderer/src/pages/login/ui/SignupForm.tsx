import { EyeIcon, EyeOffIcon, LockIcon, MailIcon, ProfileIcon } from "@/shared/ui/TonezenIcons";
import { shouldShowSignupForm } from "@/features/auth";

interface SignupFormProps {
  inviteCode: string;
  inviteVerified: boolean;
  inviteError: string | null;
  signupEmail: string;
  displayName: string;
  signupPassword: string;
  signupConfirmPassword: string;
  signupPasswordVisible: boolean;
  signupError: string | null;
  canCheckInvite: boolean;
  canCreateAccount: boolean;
  onInviteCodeChange: (value: string) => void;
  onVerifyInvite: () => void;
  onSignupEmailChange: (value: string) => void;
  onDisplayNameChange: (value: string) => void;
  onSignupPasswordChange: (value: string) => void;
  onSignupConfirmPasswordChange: (value: string) => void;
  onToggleSignupPasswordVisible: () => void;
  onSubmitSignup: () => void;
  onBackToLogin: () => void;
}

export function SignupForm({
  inviteCode,
  inviteVerified,
  inviteError,
  signupEmail,
  displayName,
  signupPassword,
  signupConfirmPassword,
  signupPasswordVisible,
  signupError,
  canCheckInvite,
  canCreateAccount,
  onInviteCodeChange,
  onVerifyInvite,
  onSignupEmailChange,
  onDisplayNameChange,
  onSignupPasswordChange,
  onSignupConfirmPasswordChange,
  onToggleSignupPasswordVisible,
  onSubmitSignup,
  onBackToLogin,
}: SignupFormProps) {
  return (
    <>
      <label className="auth-field">
        <LockIcon className="auth-field-icon" />
        <input
          type="text"
          placeholder="Инвайт-код"
          value={inviteCode}
          onChange={(e) => onInviteCodeChange(e.target.value)}
          autoComplete="one-time-code"
        />
      </label>
      {!shouldShowSignupForm(inviteVerified) && (
        <button type="button" className="auth-sign-in-btn" disabled={!canCheckInvite} onClick={onVerifyInvite}>
          Проверить код
        </button>
      )}
      {inviteError && <p className="error-text px-0.5 text-sm">{inviteError}</p>}
      {shouldShowSignupForm(inviteVerified) && (
        <>
          <label className="auth-field">
            <MailIcon className="auth-field-icon" />
            <input
              type="email"
              placeholder="Email"
              value={signupEmail}
              onChange={(e) => onSignupEmailChange(e.target.value)}
              autoComplete="email"
            />
          </label>
          <label className="auth-field">
            <ProfileIcon className="auth-field-icon" />
            <input
              type="text"
              placeholder="Имя"
              value={displayName}
              onChange={(e) => onDisplayNameChange(e.target.value)}
              autoComplete="name"
            />
          </label>
          <label className="auth-field">
            <LockIcon className="auth-field-icon" />
            <input
              type={signupPasswordVisible ? "text" : "password"}
              placeholder="Пароль"
              value={signupPassword}
              onChange={(e) => onSignupPasswordChange(e.target.value)}
              autoComplete="new-password"
            />
            <button
              type="button"
              className="auth-field-toggle"
              onClick={onToggleSignupPasswordVisible}
              aria-label={signupPasswordVisible ? "Скрыть" : "Показать"}
            >
              {signupPasswordVisible ? (
                <EyeOffIcon className="h-[19px] w-[19px] text-muted" />
              ) : (
                <EyeIcon className="h-[19px] w-[19px] text-muted" />
              )}
            </button>
          </label>
          <label className="auth-field">
            <LockIcon className="auth-field-icon" />
            <input
              type={signupPasswordVisible ? "text" : "password"}
              placeholder="Подтвердите пароль"
              value={signupConfirmPassword}
              onChange={(e) => onSignupConfirmPasswordChange(e.target.value)}
              autoComplete="new-password"
            />
          </label>
          <button type="button" className="auth-sign-in-btn" disabled={!canCreateAccount} onClick={onSubmitSignup}>
            Создать аккаунт
          </button>
        </>
      )}
      {signupError && <p className="error-text px-0.5 text-sm">{signupError}</p>}
      <button type="button" className="auth-text-button" onClick={onBackToLogin}>
        Уже есть аккаунт
      </button>
    </>
  );
}
