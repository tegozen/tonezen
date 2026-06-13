import {
  avatarCoverScale,
  type AvatarCropTransform,
} from "@shared/avatarCrop";

export async function cropAvatarToJpeg(
  image: HTMLImageElement,
  containerWidth: number,
  containerHeight: number,
  cropDiameter: number,
  transform: AvatarCropTransform,
  outputSize = 512,
  quality = 0.85,
): Promise<Uint8Array> {
  const coverScale = avatarCoverScale(
    image.naturalWidth,
    image.naturalHeight,
    containerWidth,
    containerHeight,
  );
  const displayWidth = image.naturalWidth * coverScale * transform.scale;
  const displayHeight = image.naturalHeight * coverScale * transform.scale;
  const imageLeft = (containerWidth - displayWidth) / 2 + transform.offsetX;
  const imageTop = (containerHeight - displayHeight) / 2 + transform.offsetY;
  const centerX = containerWidth / 2;
  const centerY = containerHeight / 2;
  const radius = cropDiameter / 2;

  const canvas = document.createElement("canvas");
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas unsupported");

  const sourceCanvas = document.createElement("canvas");
  sourceCanvas.width = image.naturalWidth;
  sourceCanvas.height = image.naturalHeight;
  const sourceCtx = sourceCanvas.getContext("2d");
  if (!sourceCtx) throw new Error("Canvas unsupported");
  sourceCtx.drawImage(image, 0, 0);
  const sourceData = sourceCtx.getImageData(0, 0, image.naturalWidth, image.naturalHeight);

  const imageData = ctx.createImageData(outputSize, outputSize);
  const pixels = imageData.data;

  for (let y = 0; y < outputSize; y++) {
    for (let x = 0; x < outputSize; x++) {
      const normalizedX = (x + 0.5) / outputSize - 0.5;
      const normalizedY = (y + 0.5) / outputSize - 0.5;
      const screenX = centerX + normalizedX * cropDiameter;
      const screenY = centerY + normalizedY * cropDiameter;
      const dx = screenX - centerX;
      const dy = screenY - centerY;
      const outIndex = (y * outputSize + x) * 4;
      if (dx * dx + dy * dy > radius * radius) {
        pixels[outIndex + 3] = 0;
        continue;
      }
      const bitmapX = Math.min(
        image.naturalWidth - 1,
        Math.max(0, Math.round(((screenX - imageLeft) / displayWidth) * image.naturalWidth)),
      );
      const bitmapY = Math.min(
        image.naturalHeight - 1,
        Math.max(0, Math.round(((screenY - imageTop) / displayHeight) * image.naturalHeight)),
      );
      const srcIndex = (bitmapY * image.naturalWidth + bitmapX) * 4;
      pixels[outIndex] = sourceData.data[srcIndex]!;
      pixels[outIndex + 1] = sourceData.data[srcIndex + 1]!;
      pixels[outIndex + 2] = sourceData.data[srcIndex + 2]!;
      pixels[outIndex + 3] = 255;
    }
  }

  ctx.putImageData(imageData, 0, 0);
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (value) => (value ? resolve(value) : reject(new Error("JPEG encode failed"))),
      "image/jpeg",
      quality,
    );
  });
  return new Uint8Array(await blob.arrayBuffer());
}
