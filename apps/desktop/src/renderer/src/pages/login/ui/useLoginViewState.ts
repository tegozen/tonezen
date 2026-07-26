import { useState } from "react";
import {
  canSubmitInviteCode,
  canSubmitPasswordRecovery,
  canSubmitSignup,
} from "@/features/auth";

type AuthMode = "login" | "signup" | "recovery";

interface UseLoginViewStateOptions {
  email: string;
  password: string;
  onVerifyInviteCode: (code: string) => Promise<boolean>;
  onSignup: (input: {
    inviteCode: string;
    email: string;
    password: string;
    displayName?: string;
  }) => Promise<boolean>;
  onPasswordRecovery: (email: string) => Promise<void>;
}

export function useLoginViewState({
  email,
  password,
  onVerifyInviteCode,
  onSignup,
  onPasswordRecovery,
}: UseLoginViewStateOptions) {
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
      setInviteError("Инвайт-код не подошёл");
    } finally {
      setBusy(false);
    }
  };

  const submitSignup = async () => {
    if (signupPassword !== signupConfirmPassword) {
      setSignupError("Пароли не совпадают");
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
      if (!ok) setSignupError("Не удалось зарегистрироваться");
    } catch {
      setSignupError("Не удалось зарегистрироваться");
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
      setRecoveryError("Не удалось отправить ссылку");
    } finally {
      setBusy(false);
    }
  };

  return {
    mode,
    passwordVisible,
    inviteCode,
    inviteVerified,
    inviteError,
    signupEmail,
    displayName,
    signupPassword,
    signupConfirmPassword,
    signupPasswordVisible,
    signupError,
    recoveryEmail,
    recoverySent,
    recoveryError,
    canSubmit,
    canCheckInvite,
    canCreateAccount,
    canRecover,
    switchMode,
    setPasswordVisible,
    setInviteCode,
    setInviteVerified,
    setSignupEmail,
    setDisplayName,
    setSignupPassword,
    setSignupConfirmPassword,
    setSignupPasswordVisible,
    setRecoveryEmail,
    verifyInvite,
    submitSignup,
    submitRecovery,
  };
}
