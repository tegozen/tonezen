import {
  SupabaseAuthClient,
  sessionFromGoTrue,
} from "@core/auth/supabaseAuth.js";
import type { StoredSession } from "@core/types.js";

export async function loginWithPassword(
  authClient: SupabaseAuthClient,
  email: string,
  password: string,
  withClientAvatarUrl: (session: StoredSession) => StoredSession,
): Promise<StoredSession> {
  const result = await authClient.signInWithPassword(email, password);
  const session = sessionFromGoTrue(result, email);
  return withClientAvatarUrl(session);
}

export async function verifyInviteCode(
  authClient: SupabaseAuthClient,
  code: string,
): Promise<boolean> {
  return authClient.verifyInviteCode(code);
}

export async function registerWithInvite(
  authClient: SupabaseAuthClient,
  input: {
    inviteCode: string;
    email: string;
    password: string;
    displayName?: string;
  },
  login: (email: string, password: string) => Promise<StoredSession>,
): Promise<StoredSession> {
  await authClient.signUpWithInvite(input);
  return login(input.email, input.password);
}

export async function requestPasswordRecovery(
  authClient: SupabaseAuthClient,
  email: string,
): Promise<void> {
  await authClient.requestPasswordRecovery(email);
}

export async function getReferralCode(
  authClient: SupabaseAuthClient,
  accessToken: string,
): Promise<string> {
  return authClient.getReferralCode(accessToken);
}

export async function changePassword(
  authClient: SupabaseAuthClient,
  accessToken: string,
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  await authClient.changePassword(accessToken, currentPassword, newPassword);
}
