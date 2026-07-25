import { useEffect, useRef, useState } from "react";
import {
  avatarCropDiameterPx,
  clampAvatarCropTransform,
  minAvatarCoverScale,
  avatarCoverScale,
  type AvatarCropTransform,
} from "@core/profile/avatarCrop";
import { ChevronLeftIcon } from "@/shared/ui/TonezenIcons";
import { cropAvatarToJpeg } from "../lib/cropAvatarToJpeg";

interface AvatarCropScreenProps {
  imageUrl: string;
  uploading: boolean;
  uploadError: string | null;
  onBack: () => void;
  onConfirm: (jpegBytes: Uint8Array) => void;
}

function drawAvatarCropFrame(
  canvas: HTMLCanvasElement,
  image: HTMLImageElement,
  containerWidth: number,
  containerHeight: number,
  transform: AvatarCropTransform,
  cropDiameter: number,
): void {
  const ctx = canvas.getContext("2d");
  if (!ctx || containerWidth <= 0 || containerHeight <= 0 || cropDiameter <= 0) return;

  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(containerWidth * dpr);
  canvas.height = Math.round(containerHeight * dpr);
  canvas.style.width = `${containerWidth}px`;
  canvas.style.height = `${containerHeight}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

  const coverScale = avatarCoverScale(
    image.naturalWidth,
    image.naturalHeight,
    containerWidth,
    containerHeight,
  );
  const displayWidth = image.naturalWidth * coverScale * transform.scale;
  const displayHeight = image.naturalHeight * coverScale * transform.scale;
  const left = (containerWidth - displayWidth) / 2 + transform.offsetX;
  const top = (containerHeight - displayHeight) / 2 + transform.offsetY;

  ctx.drawImage(image, left, top, displayWidth, displayHeight);

  const centerX = containerWidth / 2;
  const centerY = containerHeight / 2;
  const radius = cropDiameter / 2;

  ctx.fillStyle = "rgba(0, 0, 0, 0.68)";
  ctx.beginPath();
  ctx.rect(0, 0, containerWidth, containerHeight);
  ctx.arc(centerX, centerY, radius, 0, Math.PI * 2, true);
  ctx.fill("evenodd");

  ctx.strokeStyle = "rgba(255, 255, 255, 0.92)";
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
  ctx.stroke();
}

export function AvatarCropScreen({
  imageUrl,
  uploading,
  uploadError,
  onBack,
  onConfirm,
}: AvatarCropScreenProps) {
  const [sourceImage, setSourceImage] = useState<HTMLImageElement | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [containerSize, setContainerSize] = useState({ width: 0, height: 0 });
  const [cropTransform, setCropTransform] = useState<AvatarCropTransform>({
    scale: 1,
    offsetX: 0,
    offsetY: 0,
  });
  const [minScale, setMinScale] = useState(1);
  const cropAreaRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const draggingRef = useRef(false);
  const lastPointRef = useRef({ x: 0, y: 0 });
  const transformRef = useRef(cropTransform);

  useEffect(() => {
    transformRef.current = cropTransform;
  }, [cropTransform]);

  useEffect(() => {
    setSourceImage(null);
    setLoadError(false);
    const image = new Image();
    image.onload = () => setSourceImage(image);
    image.onerror = () => setLoadError(true);
    image.src = imageUrl;
    return () => {
      image.onload = null;
      image.onerror = null;
    };
  }, [imageUrl]);

  useEffect(() => {
    const node = cropAreaRef.current;
    if (!node) return;
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry?.contentRect ?? { width: 0, height: 0 };
      setContainerSize({ width, height });
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [sourceImage]);

  const cropDiameter = avatarCropDiameterPx(containerSize.width, containerSize.height);

  useEffect(() => {
    if (!sourceImage || containerSize.width <= 0 || cropDiameter <= 0) return;
    const coverScale = minAvatarCoverScale(
      sourceImage.naturalWidth,
      sourceImage.naturalHeight,
      containerSize.width,
      containerSize.height,
      cropDiameter,
    );
    setMinScale(coverScale);
    setCropTransform({ scale: coverScale, offsetX: 0, offsetY: 0 });
  }, [sourceImage, containerSize.width, containerSize.height, cropDiameter]);

  useEffect(() => {
    if (!sourceImage || !canvasRef.current || cropDiameter <= 0) return;
    drawAvatarCropFrame(
      canvasRef.current,
      sourceImage,
      containerSize.width,
      containerSize.height,
      cropTransform,
      cropDiameter,
    );
  }, [sourceImage, containerSize, cropTransform, cropDiameter]);

  const applyTransform = (next: AvatarCropTransform) => {
    if (!sourceImage || containerSize.width <= 0 || cropDiameter <= 0) return;
    setCropTransform(
      clampAvatarCropTransform(
        sourceImage.naturalWidth,
        sourceImage.naturalHeight,
        containerSize.width,
        containerSize.height,
        cropDiameter,
        next,
        minScale,
        minScale * 4,
      ),
    );
  };

  const handleConfirm = async () => {
    if (!sourceImage || containerSize.width <= 0 || cropDiameter <= 0 || uploading) return;
    const jpegBytes = await cropAvatarToJpeg(
      sourceImage,
      containerSize.width,
      containerSize.height,
      cropDiameter,
      cropTransform,
    );
    onConfirm(jpegBytes);
  };

  return (
    <div className="avatar-crop-screen">
      <div className="avatar-crop-top">
        <button type="button" className="overlay-back-btn" onClick={onBack}>
          <ChevronLeftIcon className="h-5 w-5 shrink-0" />
          <span>Назад</span>
        </button>
        <h2 className="avatar-crop-title">Фото профиля</h2>
        <p className="avatar-crop-hint">Масштабируйте и сдвиньте фото, чтобы лицо было в круге</p>
      </div>

      {sourceImage == null && !loadError && (
        <div className="avatar-crop-loading">
          <span className="avatar-crop-spinner" aria-hidden="true" />
        </div>
      )}

      {loadError && (
        <div className="avatar-crop-loading">
          <p className="text-sm text-muted">Не удалось открыть изображение</p>
        </div>
      )}

      {sourceImage && !loadError && (
        <>
          <div
            ref={cropAreaRef}
            className="avatar-crop-area"
            onPointerDown={(event) => {
              if (uploading) return;
              draggingRef.current = true;
              lastPointRef.current = { x: event.clientX, y: event.clientY };
              event.currentTarget.setPointerCapture(event.pointerId);
            }}
            onPointerMove={(event) => {
              if (!draggingRef.current || uploading) return;
              const dx = event.clientX - lastPointRef.current.x;
              const dy = event.clientY - lastPointRef.current.y;
              lastPointRef.current = { x: event.clientX, y: event.clientY };
              const current = transformRef.current;
              applyTransform({
                scale: current.scale,
                offsetX: current.offsetX + dx,
                offsetY: current.offsetY + dy,
              });
            }}
            onPointerUp={() => {
              draggingRef.current = false;
            }}
            onPointerCancel={() => {
              draggingRef.current = false;
            }}
            onWheel={(event) => {
              if (uploading) return;
              event.preventDefault();
              const zoom = event.deltaY < 0 ? 1.05 : 0.95;
              const current = transformRef.current;
              applyTransform({
                scale: current.scale * zoom,
                offsetX: current.offsetX,
                offsetY: current.offsetY,
              });
            }}
          >
            <canvas ref={canvasRef} className="avatar-crop-canvas" />
          </div>

          {uploadError && <p className="avatar-crop-error">{uploadError}</p>}

          <button
            type="button"
            className="account-primary-btn avatar-crop-save"
            disabled={uploading || containerSize.width <= 0 || containerSize.height <= 0}
            onClick={() => void handleConfirm()}
          >
            {uploading && <span className="avatar-crop-spinner avatar-crop-spinner-inline" aria-hidden="true" />}
            Сохранить фото
          </button>
        </>
      )}
    </div>
  );
}
