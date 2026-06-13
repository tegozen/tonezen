import { useCallback, useEffect, useState } from "react";
import { resolveLoginError } from "../lib/errorMessages";

export function useTonezenSession() {
  const [sessionState, setSessionState] = useState("Unauthenticated");
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
    (snap: {
      state: string;
      email: string | null;
      displayName: string | null;
      avatarUrl: string | null;
      memberSinceEpochMs: number | null;
    }) => {
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
    } catch (e) {
      setError(resolveLoginError(e instanceof Error ? e.message : ""));
      return false;
    }
  }, [email, password, refreshSession]);

  const logout = useCallback(async () => {
    await window.tonezen.session.logout();
    await refreshSession();
  }, [refreshSession]);

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
    logout,
  };
}
