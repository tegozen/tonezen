import { describe, expect, it } from "vitest";
import {
  canSubmitInviteCode,
  canSubmitPasswordRecovery,
  canSubmitSignup,
  shouldShowSignupForm,
} from "../src/renderer/src/lib/authFlow.js";

describe("auth flow rules", () => {
  it("hides signup form until invite code is verified", () => {
    expect(shouldShowSignupForm(false)).toBe(false);
    expect(shouldShowSignupForm(true)).toBe(true);
  });

  it("allows invite verification only with a non-empty code", () => {
    expect(canSubmitInviteCode("")).toBe(false);
    expect(canSubmitInviteCode(" ABCD1234EFGH ")).toBe(true);
  });

  it("allows signup only when required fields are valid", () => {
    expect(
      canSubmitSignup({
        inviteVerified: true,
        email: "user@example.com",
        displayName: "User",
        password: "secret123",
        confirmPassword: "secret123",
      }),
    ).toBe(true);
    expect(
      canSubmitSignup({
        inviteVerified: true,
        email: "user@example.com",
        displayName: "User",
        password: "secret123",
        confirmPassword: "different",
      }),
    ).toBe(false);
    expect(
      canSubmitSignup({
        inviteVerified: false,
        email: "user@example.com",
        displayName: "User",
        password: "secret123",
        confirmPassword: "secret123",
      }),
    ).toBe(false);
    expect(
      canSubmitSignup({
        inviteVerified: true,
        email: "user@example.com",
        displayName: " ",
        password: "secret123",
        confirmPassword: "secret123",
      }),
    ).toBe(false);
  });

  it("allows recovery request only with a non-empty email", () => {
    expect(canSubmitPasswordRecovery("")).toBe(false);
    expect(canSubmitPasswordRecovery("user@example.com")).toBe(true);
  });
});
