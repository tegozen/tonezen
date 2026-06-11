import { describe, expect, it } from "vitest";
import { WindowLifecycleManager } from "../src/main/windowLifecycle.js";

describe("WindowLifecycleManager", () => {
  it("prevents close until explicit quit", () => {
    const mgr = new WindowLifecycleManager();
    expect(mgr.shouldPreventClose()).toBe(true);
    mgr.setQuitting(true);
    expect(mgr.shouldPreventClose()).toBe(false);
  });

  it("hides on minimize", () => {
    const mgr = new WindowLifecycleManager();
    expect(mgr.shouldHideOnMinimize()).toBe(true);
  });
});
