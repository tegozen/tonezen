import type { SessionState, StoredSession } from "./types.js";

export class SessionManager {
  private refreshInFlight = false;

  constructor(
    private refreshLeadSeconds = 300,
    private clock: () => number = () => Math.floor(Date.now() / 1000),
  ) {}

  resolveState(session: StoredSession | null, isOnline: boolean): SessionState {
    if (!session) return "Unauthenticated";
    if (!isOnline) return "AuthenticatedOffline";
    return this.isExpired(session) ? "AuthenticatedStale" : "AuthenticatedOnline";
  }

  shouldRefresh(session: StoredSession | null, isOnline: boolean): boolean {
    if (!session || !isOnline) return false;
    return this.clock() >= session.expiresAtEpochSeconds - this.refreshLeadSeconds;
  }

  isExpired(session: StoredSession): boolean {
    return this.clock() >= session.expiresAtEpochSeconds;
  }

  beginRefresh(): boolean {
    if (this.refreshInFlight) return false;
    this.refreshInFlight = true;
    return true;
  }

  endRefresh(): void {
    this.refreshInFlight = false;
  }
}
