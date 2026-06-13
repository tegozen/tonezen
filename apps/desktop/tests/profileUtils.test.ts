import { describe, expect, it } from "vitest";
import {
  formatLastSyncLabel,
  formatMemberSinceDate,
  formatSyncTime,
} from "../src/renderer/src/lib/profileUtils.js";

describe("formatMemberSinceDate", () => {
  it("formats epoch ms as dd.MM.yyyy", () => {
    expect(formatMemberSinceDate(Date.UTC(2026, 5, 12))).toBe("12.06.2026");
  });

  it("returns null for missing values", () => {
    expect(formatMemberSinceDate(null)).toBeNull();
  });
});

describe("formatSyncTime", () => {
  it("formats local time as H:mm", () => {
    const date = new Date();
    date.setHours(2, 57, 0, 0);
    expect(formatSyncTime(date.getTime())).toBe("2:57");
  });
});

describe("formatLastSyncLabel", () => {
  it("includes time when last sync is known", () => {
    const date = new Date();
    date.setHours(14, 5, 0, 0);
    expect(
      formatLastSyncLabel(date.getTime(), {
        todayAt: "Последняя синхронизация: сегодня, {time}",
        never: "Синхронизация ещё не выполнялась",
      }),
    ).toBe("Последняя синхронизация: сегодня, 14:05");
  });

  it("falls back to never label", () => {
    expect(
      formatLastSyncLabel(null, {
        todayAt: "Последняя синхронизация: сегодня, {time}",
        never: "Синхронизация ещё не выполнялась",
      }),
    ).toBe("Синхронизация ещё не выполнялась");
  });
});
