export interface AvatarCropTransform {
  scale: number;
  offsetX: number;
  offsetY: number;
}

export function avatarCoverScale(
  bitmapWidth: number,
  bitmapHeight: number,
  containerWidth: number,
  containerHeight: number,
): number {
  return Math.max(containerWidth / bitmapWidth, containerHeight / bitmapHeight);
}

export function minAvatarCoverScale(
  bitmapWidth: number,
  bitmapHeight: number,
  containerWidth: number,
  containerHeight: number,
  cropDiameter: number,
): number {
  if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
    return 1;
  }
  const coverScale = avatarCoverScale(bitmapWidth, bitmapHeight, containerWidth, containerHeight);
  const displayWidth = bitmapWidth * coverScale;
  const displayHeight = bitmapHeight * coverScale;
  const scaleToCoverWidth = cropDiameter / displayWidth;
  const scaleToCoverHeight = cropDiameter / displayHeight;
  return Math.max(scaleToCoverWidth, scaleToCoverHeight, 1);
}

export function clampAvatarCropTransform(
  bitmapWidth: number,
  bitmapHeight: number,
  containerWidth: number,
  containerHeight: number,
  cropDiameter: number,
  transform: AvatarCropTransform,
  minScale: number,
  maxScale: number,
): AvatarCropTransform {
  const scale = Math.min(Math.max(transform.scale, minScale), maxScale);
  const coverScale = avatarCoverScale(bitmapWidth, bitmapHeight, containerWidth, containerHeight);
  const displayWidth = bitmapWidth * coverScale * scale;
  const displayHeight = bitmapHeight * coverScale * scale;
  const maxOffsetX = Math.max(0, (displayWidth - cropDiameter) / 2);
  const maxOffsetY = Math.max(0, (displayHeight - cropDiameter) / 2);
  return {
    scale,
    offsetX: Math.min(Math.max(transform.offsetX, -maxOffsetX), maxOffsetX),
    offsetY: Math.min(Math.max(transform.offsetY, -maxOffsetY), maxOffsetY),
  };
}

export function avatarCropDiameterPx(containerWidth: number, containerHeight: number): number {
  if (containerWidth <= 0 || containerHeight <= 0) return 0;
  return Math.min(containerWidth, containerHeight) * 0.78;
}
