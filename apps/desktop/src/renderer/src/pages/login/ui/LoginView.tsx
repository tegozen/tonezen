import { AuthIntroPanel, AuthStarField } from "@/shared/ui/AuthDecor";
import { LoginForm } from "./LoginForm";
import { SignupForm } from "./SignupForm";
import { RecoveryForm } from "./RecoveryForm";
import { useLoginViewState } from "./useLoginViewState";

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
  const state = useLoginViewState({
    email,
    password,
    onVerifyInviteCode,
    onSignup,
    onPasswordRecovery,
  });

  return (
    <div className="app-frame auth-screen">
      <AuthStarField />
      <main className="auth-content">
        <AuthIntroPanel />
        <form
          className="auth-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (state.mode === "login" && state.canSubmit) onLogin();
            if (state.mode === "signup") void state.submitSignup();
            if (state.mode === "recovery") void state.submitRecovery();
          }}
        >
          {state.mode === "login" && (
            <LoginForm
              email={email}
              password={password}
              passwordVisible={state.passwordVisible}
              error={error}
              canSubmit={state.canSubmit}
              onEmailChange={onEmailChange}
              onPasswordChange={onPasswordChange}
              onTogglePasswordVisible={() => state.setPasswordVisible((value) => !value)}
              onForgotPassword={() => state.switchMode("recovery")}
              onNoAccount={() => state.switchMode("signup")}
            />
          )}
          {state.mode === "signup" && (
            <SignupForm
              inviteCode={state.inviteCode}
              inviteVerified={state.inviteVerified}
              inviteError={state.inviteError}
              signupEmail={state.signupEmail}
              displayName={state.displayName}
              signupPassword={state.signupPassword}
              signupConfirmPassword={state.signupConfirmPassword}
              signupPasswordVisible={state.signupPasswordVisible}
              signupError={state.signupError}
              canCheckInvite={state.canCheckInvite}
              canCreateAccount={state.canCreateAccount}
              onInviteCodeChange={(value) => {
                state.setInviteCode(value);
                state.setInviteVerified(false);
              }}
              onVerifyInvite={() => void state.verifyInvite()}
              onSignupEmailChange={state.setSignupEmail}
              onDisplayNameChange={state.setDisplayName}
              onSignupPasswordChange={state.setSignupPassword}
              onSignupConfirmPasswordChange={state.setSignupConfirmPassword}
              onToggleSignupPasswordVisible={() =>
                state.setSignupPasswordVisible((value) => !value)
              }
              onSubmitSignup={() => void state.submitSignup()}
              onBackToLogin={() => state.switchMode("login")}
            />
          )}
          {state.mode === "recovery" && (
            <RecoveryForm
              recoveryEmail={state.recoveryEmail}
              recoverySent={state.recoverySent}
              recoveryError={state.recoveryError}
              canRecover={state.canRecover}
              onRecoveryEmailChange={state.setRecoveryEmail}
              onSubmitRecovery={() => void state.submitRecovery()}
              onBackToLogin={() => state.switchMode("login")}
            />
          )}
          <p className="auth-footer-note">
            Офлайн-воспроизведение работает с загруженными файлами. Истёкшая сессия остаётся активной офлайн.
          </p>
        </form>
      </main>
    </div>
  );
}
