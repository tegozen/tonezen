import { describe, expect, it, vi } from "vitest";
import { createRefreshCoordinator } from "../src/shared/refreshCoordinator.js";

describe("createRefreshCoordinator", () => {
  it("runs refresh once for concurrent callers", async () => {
    const coordinator = createRefreshCoordinator<string>();
    let runs = 0;
    const refresh = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
      runs += 1;
      return "refreshed";
    });

    const [first, second] = await Promise.all([
      coordinator.coalesce(() => true, refresh, () => "current"),
      coordinator.coalesce(() => true, refresh, () => "current"),
    ]);

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(runs).toBe(1);
    expect(first).toBe("refreshed");
    expect(second).toBe("refreshed");
  });

  it("waits for in-flight refresh even when refresh is not required", async () => {
    const coordinator = createRefreshCoordinator<number>();
    let refreshed = false;
    const refresh = vi.fn(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
      refreshed = true;
      return 2;
    });

    const refreshPromise = coordinator.coalesce(() => true, refresh, () => 1);
    await new Promise((resolve) => setTimeout(resolve, 5));
    const waiter = coordinator.coalesce(() => false, refresh, () => (refreshed ? 2 : 1));

    const [started, waited] = await Promise.all([refreshPromise, waiter]);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(started).toBe(2);
    expect(waited).toBe(2);
  });

  it("skips refresh when token is still fresh", async () => {
    const coordinator = createRefreshCoordinator<string>();
    const refresh = vi.fn(async () => "refreshed");

    const result = await coordinator.coalesce(() => false, refresh, () => "current");

    expect(result).toBe("current");
    expect(refresh).not.toHaveBeenCalled();
  });
});
