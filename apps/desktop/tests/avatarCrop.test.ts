import { describe, expect, it } from "vitest";
import {
  avatarCoverScale,
  clampAvatarCropTransform,
  minAvatarCoverScale,
  type AvatarCropTransform,
} from "../src/shared/avatarCrop.js";

describe("avatarCrop", () => {
  it("minAvatarCoverScale ensures crop circle fits inside displayed image", () => {
    const scale = minAvatarCoverScale(800, 600, 400, 500, 200);
    const coverScale = avatarCoverScale(800, 600, 400, 500);
    const displayWidth = 800 * coverScale * scale;
    const displayHeight = 600 * coverScale * scale;
    expect(displayWidth).toBeGreaterThanOrEqual(200);
    expect(displayHeight).toBeGreaterThanOrEqual(200);
  });

  it("clampAvatarCropTransform limits pan within image bounds", () => {
    const clamped = clampAvatarCropTransform(
      1000,
      1000,
      400,
      400,
      240,
      { scale: 2, offsetX: 500, offsetY: -500 },
      1.2,
      4,
    );
    expect(clamped.scale).toBeCloseTo(2, 3);
    const coverScale = avatarCoverScale(1000, 1000, 400, 400);
    const displayWidth = 1000 * coverScale * clamped.scale;
    const displayHeight = 1000 * coverScale * clamped.scale;
    const maxOffsetX = Math.max(0, (displayWidth - 240) / 2);
    const maxOffsetY = Math.max(0, (displayHeight - 240) / 2);
    expect(Math.abs(clamped.offsetX)).toBeLessThanOrEqual(maxOffsetX + 0.01);
    expect(Math.abs(clamped.offsetY)).toBeLessThanOrEqual(maxOffsetY + 0.01);
  });

  it("clampAvatarCropTransform coerces scale to range", () => {
    const clamped = clampAvatarCropTransform(
      500,
      500,
      300,
      300,
      180,
      { scale: 10, offsetX: 0, offsetY: 0 },
      1.5,
      3,
    );
    expect(clamped.scale).toBeCloseTo(3, 3);
  });
});
