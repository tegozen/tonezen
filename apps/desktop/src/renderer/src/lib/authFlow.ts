export interface SignupFormState {
  inviteVerified: boolean;
  email: string;
  displayName: string;
  password: string;
  confirmPassword: string;
}

export function shouldShowSignupForm(inviteVerified: boolean): boolean {
  return inviteVerified;
}

export function canSubmitInviteCode(code: string): boolean {
  return code.trim().length > 0;
}

export function canSubmitSignup(state: SignupFormState): boolean {
  return (
    state.inviteVerified &&
    state.email.trim().length > 0 &&
    state.displayName.trim().length > 0 &&
    state.password.length >= 6 &&
    state.password === state.confirmPassword
  );
}

export function canSubmitPasswordRecovery(email: string): boolean {
  return email.trim().length > 0;
}
