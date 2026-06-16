import { describe, expect, it } from "vitest";
import { resolveResumeAction } from "../src/shared/downloadResumePolicy.js";

describe("downloadResumePolicy", () => {
  it("appends on 206", () => {
    expect(resolveResumeAction(100, 100, 1000, 206)).toBe("RANGE_APPEND");
  });

  it("restarts on 200", () => {
    expect(resolveResumeAction(100, 100, 1000, 200)).toBe("RESTART");
  });

  it("restarts when part exceeds total", () => {
    expect(resolveResumeAction(2000, 2000, 1000, 206)).toBe("RESTART");
  });
});
