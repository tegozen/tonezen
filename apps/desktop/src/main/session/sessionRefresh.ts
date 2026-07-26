import { SessionManager } from "@core/auth/session.js";
import {
  SupabaseAuthClient,
  mergeSessionOnRefresh,
  sessionFromGoTrue,
} from "@core/auth/supabaseAuth.js";
import type { SessionState, StoredSession } from "@core/types.js";
import { isRefreshAuthFailure } from "@core/auth/authErrors.js";
import { createRefreshCoordinator } from "@core/auth/refreshCoordinator.js";

export interface SessionRefreshDeps {
  getSession(): StoredSession | null;
  setSession(session: StoredSession | null): void;
  getOnline(): boolean;
  getAuthClient(): SupabaseAuthClient | null;
  getManager(): SessionManager;
  logout(): void;
  persist(session: StoredSession): void;
  withClientAvatarUrl(session: StoredSession): StoredSession;
}

export function createSessionRefresh(deps: SessionRefreshDeps) {
  const refreshCoordinator = createRefreshCoordinator<SessionState>();

  async function performRefresh(): Promise<SessionState> {
    const session = deps.getSession();
    if (!session) return "Unauthenticated";
    const userId = session.userId;
    try {
      if (!deps.getOnline()) {
        return deps.getManager().resolveState(session, false);
      }
      const authClient = deps.getAuthClient();
      if (!authClient || !session.refreshToken) {
        return "Unauthenticated";
      }
      const result = await authClient.refreshSession(session.refreshToken);
      // Logout (or account switch) may have cleared session while refresh was in flight.
      const current = deps.getSession();
      if (!current || current.userId !== userId) {
        return current
          ? deps.getManager().resolveState(current, deps.getOnline())
          : "Unauthenticated";
      }
      const next = sessionFromGoTrue(result, session.email);
      const merged = deps.withClientAvatarUrl(mergeSessionOnRefresh(current, next));
      deps.setSession(merged);
      deps.persist(merged);
      return "AuthenticatedOnline";
    } catch (error) {
      if (isRefreshAuthFailure(error)) {
        deps.logout();
        return "Unauthenticated";
      }
      return deps.getManager().resolveState(deps.getSession() ?? session, deps.getOnline());
    }
  }

  async function refreshIfNeeded(): Promise<SessionState> {
    if (!deps.getSession()) return "Unauthenticated";
    return refreshCoordinator.coalesce(
      () => {
        const session = deps.getSession();
        if (!session) return false;
        return (
          deps.getManager().shouldRefresh(session, deps.getOnline()) ||
          (deps.getOnline() && deps.getManager().isExpired(session))
        );
      },
      () => performRefresh(),
      () =>
        deps.getSession()
          ? deps.getManager().resolveState(deps.getSession()!, deps.getOnline())
          : "Unauthenticated",
    );
  }

  return { refreshIfNeeded };
}
