package com.tonezen.app.domain.avatar

import kotlin.math.max
import kotlin.math.min

data class AvatarCropTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

internal fun avatarCoverScale(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
): Float = max(containerWidth / bitmapWidth, containerHeight / bitmapHeight)

fun minAvatarCoverScale(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
    cropDiameter: Float,
): Float {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
        return 1f
    }
    val coverScale = avatarCoverScale(bitmapWidth, bitmapHeight, containerWidth, containerHeight)
    val displayWidth = bitmapWidth * coverScale
    val displayHeight = bitmapHeight * coverScale
    val scaleToCoverWidth = cropDiameter / displayWidth
    val scaleToCoverHeight = cropDiameter / displayHeight
    return max(scaleToCoverWidth, scaleToCoverHeight).coerceAtLeast(1f)
}

fun clampAvatarCropTransform(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerWidth: Float,
    containerHeight: Float,
    cropDiameter: Float,
    transform: AvatarCropTransform,
    minScale: Float,
    maxScale: Float,
): AvatarCropTransform {
    val scale = transform.scale.coerceIn(minScale, maxScale)
    val coverScale = avatarCoverScale(bitmapWidth, bitmapHeight, containerWidth, containerHeight)
    val displayWidth = bitmapWidth * coverScale * scale
    val displayHeight = bitmapHeight * coverScale * scale
    val maxOffsetX = max(0f, (displayWidth - cropDiameter) / 2f)
    val maxOffsetY = max(0f, (displayHeight - cropDiameter) / 2f)
    return AvatarCropTransform(
        scale = scale,
        offsetX = transform.offsetX.coerceIn(-maxOffsetX, maxOffsetX),
        offsetY = transform.offsetY.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

fun avatarCropDiameterPx(containerWidth: Float, containerHeight: Float): Float {
    if (containerWidth <= 0f || containerHeight <= 0f) return 0f
    return min(containerWidth, containerHeight) * 0.78f
}
