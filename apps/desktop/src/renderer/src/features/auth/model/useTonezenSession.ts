import { useCallback, useEffect, useState } from "react";
import type { SessionState } from "@core/types";

type SessionSnapshot = Awaited<ReturnType<typeof window.tonezen.session.get>>;

export function useTonezenSession() {
  const [sessionState, setSessionState] = useState<SessionState>("Unauthenticated");
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [memberSinceEpochMs, setMemberSinceEpochMs] = useState<number | null>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const refreshSession = useCallback(async () => {
    const online = navigator.onLine;
    await window.tonezen.session.setOnline(online);
    const snap = await window.tonezen.session.get();
    setSessionState(snap.state);
    setUserEmail(snap.email);
    setDisplayName(snap.displayName);
    setAvatarUrl(snap.avatarUrl);
    setMemberSinceEpochMs(snap.memberSinceEpochMs);
  }, []);

  const applySnapshot = useCallback(
    (snap: SessionSnapshot) => {
      setSessionState(snap.state);
      setUserEmail(snap.email);
      setDisplayName(snap.displayName);
      setAvatarUrl(snap.avatarUrl);
      setMemberSinceEpochMs(snap.memberSinceEpochMs);
    },
    [],
  );

  useEffect(() => {
    void refreshSession();
    const onOnline = () => void refreshSession();
    const onOffline = () => void refreshSession();
    const unsubscribeProfile = window.tonezen.session.onProfileUpdated(applySnapshot);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      unsubscribeProfile();
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, [refreshSession, applySnapshot]);

  const login = useCallback(async () => {
    try {
      setError(null);
      await window.tonezen.session.login(email, password);
      await refreshSession();
      return true;
    } catch {
      setError("Не удалось войти. Проверьте email и пароль.");
      return false;
    }
  }, [email, password, refreshSession]);

  const verifyInviteCode = useCallback(async (code: string) => {
    return window.tonezen.session.verifyInviteCode(code);
  }, []);

  const registerWithInvite = useCallback(
    async (input: {
      inviteCode: string;
      email: string;
      password: string;
      displayName?: string;
    }) => {
      try {
        setError(null);
        await window.tonezen.session.register(input);
        await refreshSession();
        return true;
      } catch {
        setError("Не удалось войти. Проверьте email и пароль.");
        return false;
      }
    },
    [refreshSession],
  );

  const requestPasswordRecovery = useCallback(async (emailAddress: string) => {
    await window.tonezen.session.requestPasswordRecovery(emailAddress);
  }, []);

  const logout = useCallback(async () => {
    try {
      const snap = await window.tonezen.session.logout();
      applySnapshot(snap);
    } catch {
      // Main may have cleared the session even if IPC reported an error.
      setSessionState("Unauthenticated");
      setUserEmail(null);
      setDisplayName(null);
      setAvatarUrl(null);
      setMemberSinceEpochMs(null);
    }
  }, [applySnapshot]);

  return {
    sessionState,
    userEmail,
    displayName,
    avatarUrl,
    memberSinceEpochMs,
    email,
    setEmail,
    password,
    setPassword,
    error,
    setError,
    refreshSession,
    login,
    verifyInviteCode,
    registerWithInvite,
    requestPasswordRecovery,
    logout,
  };
}
