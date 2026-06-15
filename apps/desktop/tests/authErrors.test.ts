import { describe, expect, it } from "vitest";
import { isRefreshAuthFailure } from "../src/shared/authErrors.js";

describe("isRefreshAuthFailure", () => {
  it("treats GoTrue refresh 400 as auth failure", () => {
    expect(isRefreshAuthFailure(new Error('Auth failed (400): {"error":"invalid_grant"}'))).toBe(
      true,
    );
  });

  it("treats 401 and 403 as auth failure", () => {
    expect(isRefreshAuthFailure(new Error("Auth failed (401): unauthorized"))).toBe(true);
    expect(isRefreshAuthFailure(new Error("Auth failed (403): forbidden"))).toBe(true);
  });

  it("ignores unrelated errors", () => {
    expect(isRefreshAuthFailure(new Error("network timeout"))).toBe(false);
    expect(isRefreshAuthFailure(new Error("Auth failed (500): internal"))).toBe(false);
  });
});
