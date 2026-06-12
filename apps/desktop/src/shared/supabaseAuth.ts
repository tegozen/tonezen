export interface GoTrueSession {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
  user: { id: string; email?: string };
}

export interface AuthConfig {
  baseUrl: string;
  anonKey: string;
}

export class SupabaseAuthClient {
  constructor(private config: AuthConfig) {}

  async signInWithPassword(email: string, password: string): Promise<GoTrueSession> {
    return this.tokenRequest({ grant_type: "password", email, password });
  }

  async refreshSession(refreshToken: string): Promise<GoTrueSession> {
    return this.tokenRequest({ grant_type: "refresh_token", refresh_token: refreshToken });
  }

  private async tokenRequest(body: Record<string, string>): Promise<GoTrueSession> {
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/auth/v1/token?grant_type=${body.grant_type}`;
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: this.config.anonKey,
        Authorization: `Bearer ${this.config.anonKey}`,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Auth failed (${response.status}): ${text}`);
    }
    return (await response.json()) as GoTrueSession;
  }
}

export function sessionFromGoTrue(result: GoTrueSession): {
  userId: string;
  accessToken: string;
  refreshToken: string;
  expiresAtEpochSeconds: number;
} {
  return {
    userId: result.user.id,
    accessToken: result.access_token,
    refreshToken: result.refresh_token,
    expiresAtEpochSeconds: Math.floor(Date.now() / 1000) + result.expires_in,
  };
}
