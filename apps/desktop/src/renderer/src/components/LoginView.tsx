import { useState } from "react";
import { EyeIcon, EyeOffIcon, LockIcon, MailIcon, ProfileIcon } from "./TonezenIcons";
import { AuthIntroPanel, AuthStarField } from "./AuthDecor";
import { strings } from "../i18n/strings";
import {
  canSubmitInviteCode,
  canSubmitPasswordRecovery,
  canSubmitSignup,
  shouldShowSignupForm,
} from "../lib/authFlow";

interface LoginViewProps {
  email: string;
  password: string;
  error: string | null;
  onEmailChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
  onLogin: () => void;
  onVerifyInviteCode: (code: string) => Promise<boolean>;
  onSignup: (input: {
    inviteCode: string;
    email: string;
    password: string;
    displayName?: string;
  }) => Promise<boolean>;
  onPasswordRecovery: (email: string) => Promise<void>;
}

type AuthMode = "login" | "signup" | "recovery";

export function LoginView({
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onLogin,
  onVerifyInviteCode,
  onSignup,
  onPasswordRecovery,
}: LoginViewProps) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [inviteCode, setInviteCode] = useState("");
  const [inviteVerified, setInviteVerified] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [signupEmail, setSignupEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [signupPassword, setSignupPassword] = useState("");
  const [signupConfirmPassword, setSignupConfirmPassword] = useState("");
  const [signupPasswordVisible, setSignupPasswordVisible] = useState(false);
  const [signupError, setSignupError] = useState<string | null>(null);
  const [recoveryEmail, setRecoveryEmail] = useState("");
  const [recoverySent, setRecoverySent] = useState(false);
  const [recoveryError, setRecoveryError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const canSubmit = email.trim().length > 0 && password.length > 0;
  const canCheckInvite = canSubmitInviteCode(inviteCode) && !busy;
  const canCreateAccount =
    canSubmitSignup({
      inviteVerified,
      email: signupEmail,
      displayName,
      password: signupPassword,
      confirmPassword: signupConfirmPassword,
    }) && !busy;
  const canRecover = canSubmitPasswordRecovery(recoveryEmail) && !busy;

  const switchMode = (nextMode: AuthMode) => {
    setMode(nextMode);
    setInviteError(null);
    setSignupError(null);
    setRecoveryError(null);
    setRecoverySent(false);
  };

  const verifyInvite = async () => {
    if (!canCheckInvite) return;
    setBusy(true);
    setInviteError(null);
    try {
      await onVerifyInviteCode(inviteCode.trim());
      setInviteVerified(true);
    } catch {
      setInviteVerified(false);
      setInviteError(strings.inviteCodeInvalid);
    } finally {
      setBusy(false);
    }
  };

  const submitSignup = async () => {
    if (signupPassword !== signupConfirmPassword) {
      setSignupError(strings.passwordMismatch);
      return;
    }
    if (!canCreateAccount) return;
    setBusy(true);
    setSignupError(null);
    try {
      const ok = await onSignup({
        inviteCode: inviteCode.trim(),
        email: signupEmail.trim(),
        password: signupPassword,
        displayName: displayName.trim(),
      });
      if (!ok) setSignupError(strings.signupFailed);
    } catch {
      setSignupError(strings.signupFailed);
    } finally {
      setBusy(false);
    }
  };

  const submitRecovery = async () => {
    if (!canRecover) return;
    setBusy(true);
    setRecoveryError(null);
    try {
      await onPasswordRecovery(recoveryEmail.trim());
      setRecoverySent(true);
    } catch {
      setRecoveryError(strings.recoveryFailed);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app-frame auth-screen">
      <AuthStarField />
      <main className="auth-content">
        <AuthIntroPanel />
        <form
          className="auth-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (mode === "login" && canSubmit) onLogin();
            if (mode === "signup") void submitSignup();
            if (mode === "recovery") void submitRecovery();
          }}
        >
          {mode === "login" && (
            <>
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
              <div className="auth-inline-actions">
                <button type="button" onClick={() => switchMode("recovery")}>
                  {strings.forgotPassword}
                </button>
                <button type="button" onClick={() => switchMode("signup")}>
                  {strings.noAccountYet}
                </button>
              </div>
            </>
          )}
          {mode === "signup" && (
            <>
              <label className="auth-field">
                <LockIcon className="auth-field-icon" />
                <input
                  type="text"
                  placeholder={strings.inviteCode}
                  value={inviteCode}
                  onChange={(e) => {
                    setInviteCode(e.target.value);
                    setInviteVerified(false);
                  }}
                  autoComplete="one-time-code"
                />
              </label>
              {!shouldShowSignupForm(inviteVerified) && (
                <button type="button" className="auth-sign-in-btn" disabled={!canCheckInvite} onClick={verifyInvite}>
                  {strings.checkInviteCode}
                </button>
              )}
              {inviteError && <p className="error-text px-0.5 text-sm">{inviteError}</p>}
              {shouldShowSignupForm(inviteVerified) && (
                <>
                  <label className="auth-field">
                    <MailIcon className="auth-field-icon" />
                    <input
                      type="email"
                      placeholder={strings.email}
                      value={signupEmail}
                      onChange={(e) => setSignupEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </label>
                  <label className="auth-field">
                    <ProfileIcon className="auth-field-icon" />
                    <input
                      type="text"
                      placeholder={strings.displayName}
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      autoComplete="name"
                    />
                  </label>
                  <label className="auth-field">
                    <LockIcon className="auth-field-icon" />
                    <input
                      type={signupPasswordVisible ? "text" : "password"}
                      placeholder={strings.password}
                      value={signupPassword}
                      onChange={(e) => setSignupPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      className="auth-field-toggle"
                      onClick={() => setSignupPasswordVisible((value) => !value)}
                      aria-label={signupPasswordVisible ? strings.hidePassword : strings.showPassword}
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
                      placeholder={strings.confirmPassword}
                      value={signupConfirmPassword}
                      onChange={(e) => setSignupConfirmPassword(e.target.value)}
                      autoComplete="new-password"
                    />
                  </label>
                  <button type="button" className="auth-sign-in-btn" disabled={!canCreateAccount} onClick={submitSignup}>
                    {strings.createAccount}
                  </button>
                </>
              )}
              {signupError && <p className="error-text px-0.5 text-sm">{signupError}</p>}
              <button type="button" className="auth-text-button" onClick={() => switchMode("login")}>
                {strings.alreadyHaveAccount}
              </button>
            </>
          )}
          {mode === "recovery" && (
            <>
              <h2 className="auth-mode-title">{strings.recoveryTitle}</h2>
              <p className="auth-mode-copy">{strings.recoveryBody}</p>
              <label className="auth-field">
                <MailIcon className="auth-field-icon" />
                <input
                  type="email"
                  placeholder={strings.email}
                  value={recoveryEmail}
                  onChange={(e) => setRecoveryEmail(e.target.value)}
                  autoComplete="email"
                />
              </label>
              <button type="button" className="auth-sign-in-btn" disabled={!canRecover} onClick={submitRecovery}>
                {strings.sendRecoveryEmail}
              </button>
              {recoverySent && <p className="success-text px-0.5 text-sm">{strings.recoverySent}</p>}
              {recoveryError && <p className="error-text px-0.5 text-sm">{recoveryError}</p>}
              <button type="button" className="auth-text-button" onClick={() => switchMode("login")}>
                {strings.backToSignIn}
              </button>
            </>
          )}
          <p className="auth-footer-note">{strings.offlinePlaybackNote}</p>
        </form>
      </main>
    </div>
  );
}
