import { useState } from "react";
import { EyeIcon, EyeOffIcon, LockIcon, MailIcon } from "./TonezenIcons";
import { AuthIntroPanel, AuthStarField } from "./AuthDecor";
import { strings } from "../i18n/strings";

interface LoginViewProps {
  email: string;
  password: string;
  error: string | null;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onLogin: () => void;
}

export function LoginView({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onLogin,
}: LoginViewProps) {
  const [passwordVisible, setPasswordVisible] = useState(false);
  const canSubmit = email.trim().length > 0 && password.length > 0;

  return (
    <div className="app-frame auth-screen">
      <AuthStarField />
      <main className="auth-content">
        <AuthIntroPanel />
        <form
          className="auth-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (canSubmit) onLogin();
          }}
        >
          <label className="auth-field">
            <MailIcon className="auth-field-icon" />
            <input
              type="email"
              placeholder={strings.email}
              value={email}
              onChange={(e) => onEmailChange(e.target.value)}
              autoComplete="email"
            />
          </label>
          <label className="auth-field">
            <LockIcon className="auth-field-icon" />
            <input
              type={passwordVisible ? "text" : "password"}
              placeholder={strings.password}
              value={password}
              onChange={(e) => onPasswordChange(e.target.value)}
              autoComplete="current-password"
            />
            <button
              type="button"
              className="auth-field-toggle"
              onClick={() => setPasswordVisible((value) => !value)}
              aria-label={passwordVisible ? strings.hidePassword : strings.showPassword}
            >
              {passwordVisible ? (
                <EyeOffIcon className="h-[19px] w-[19px] text-muted" />
              ) : (
                <EyeIcon className="h-[19px] w-[19px] text-muted" />
              )}
            </button>
          </label>
          <button type="submit" className="auth-sign-in-btn" disabled={!canSubmit}>
            {strings.signIn}
          </button>
          {error && <p className="error-text px-0.5 text-sm">{error}</p>}
          <p className="auth-footer-note">{strings.offlinePlaybackNote}</p>
        </form>
      </main>
    </div>
  );
}
