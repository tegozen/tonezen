export interface AuthAdminConfig {
  authUrl: string;
  publicBaseUrl: string;
  serviceRoleKey: string;
}

interface CreatedAuthUser {
  id: string;
  email: string;
}

function trimTrailingSlash(value: string): string {
  return value.replace(/\/$/, "");
}

function serviceHeaders(serviceRoleKey: string, authorization = serviceRoleKey): Record<string, string> {
  return {
    "Content-Type": "application/json",
    apikey: serviceRoleKey,
    Authorization: `Bearer ${authorization}`,
  };
}

export class AuthAdminClient {
  constructor(private config: AuthAdminConfig) {}

  async createConfirmedUser(input: {
    email: string;
    password: string;
    displayName?: string;
  }): Promise<CreatedAuthUser | { error: "duplicate_email" }> {
    const url = `${trimTrailingSlash(this.config.authUrl)}/admin/users`;
    const body: Record<string, unknown> = {
      email: input.email,
      password: input.password,
      email_confirm: true,
    };
    if (input.displayName?.trim()) {
      body.user_metadata = { full_name: input.displayName.trim() };
    }

    const response = await fetch(url, {
      method: "POST",
      headers: serviceHeaders(this.config.serviceRoleKey),
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      if (response.status === 409 || /already|exists|registered/i.test(text)) {
        return { error: "duplicate_email" };
      }
      throw new Error(`Create auth user failed (${response.status}): ${text}`);
    }
    const user = (await response.json()) as { id?: unknown; email?: unknown };
    return {
      id: String(user.id),
      email: typeof user.email === "string" ? user.email : input.email,
    };
  }

  async sendPasswordRecovery(email: string): Promise<void> {
    const url = `${trimTrailingSlash(this.config.authUrl)}/recover`;
    const response = await fetch(url, {
      method: "POST",
      headers: serviceHeaders(this.config.serviceRoleKey),
      body: JSON.stringify({
        email,
        redirect_to: `${trimTrailingSlash(this.config.publicBaseUrl)}/reset-password`,
      }),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Password recovery failed (${response.status}): ${text}`);
    }
  }

  async updatePasswordWithRecoveryToken(
    accessToken: string,
    password: string,
  ): Promise<"ok" | "invalid_token"> {
    const url = `${trimTrailingSlash(this.config.authUrl)}/user`;
    const response = await fetch(url, {
      method: "PUT",
      headers: serviceHeaders(this.config.serviceRoleKey, accessToken),
      body: JSON.stringify({ password }),
    });
    if (response.ok) return "ok";
    const text = await response.text();
    if (response.status === 401 || response.status === 403) return "invalid_token";
    throw new Error(`Password update failed (${response.status}): ${text}`);
  }
}
