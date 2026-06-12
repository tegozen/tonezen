import { useCallback, useEffect, useState } from "react";

export function useTonezenSession() {
  const [sessionState, setSessionState] = useState("Unauthenticated");
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState<string | null>(null);
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
  }, []);

  useEffect(() => {
    refreshSession();
    const onOnline = () => refreshSession();
    const onOffline = () => refreshSession();
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, [refreshSession]);

  const login = useCallback(async () => {
    try {
      setError(null);
      const snap = await window.tonezen.session.login(email, password);
      setSessionState(snap.state);
      setUserEmail(snap.email);
      setDisplayName(snap.displayName);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : "Login failed");
      return false;
    }
  }, [email, password]);

  const logout = useCallback(async () => {
    await window.tonezen.session.logout();
    await refreshSession();
  }, [refreshSession]);

  return {
    sessionState,
    userEmail,
    displayName,
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
