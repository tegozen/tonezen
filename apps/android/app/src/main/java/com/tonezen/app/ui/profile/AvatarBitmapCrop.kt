package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import com.tonezen.app.domain.avatar.AvatarCropTransform
import com.tonezen.app.domain.avatar.avatarCoverScale
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

fun cropAvatarToJpeg(
    bitmap: Bitmap,
    containerWidth: Float,
    containerHeight: Float,
    cropDiameter: Float,
    transform: AvatarCropTransform,
    outputSize: Int = 512,
    quality: Int = 85,
): ByteArray {
    require(containerWidth > 0f && containerHeight > 0f && cropDiameter > 0f) {
        "Invalid crop container"
    }
    val coverScale = avatarCoverScale(bitmap.width, bitmap.height, containerWidth, containerHeight)
    val displayWidth = bitmap.width * coverScale * transform.scale
    val displayHeight = bitmap.height * coverScale * transform.scale
    val imageLeft = (containerWidth - displayWidth) / 2f + transform.offsetX
    val imageTop = (containerHeight - displayHeight) / 2f + transform.offsetY
    val centerX = containerWidth / 2f
    val centerY = containerHeight / 2f
    val radius = cropDiameter / 2f
    val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(outputSize * outputSize)
    for (y in 0 until outputSize) {
        for (x in 0 until outputSize) {
            val normalizedX = (x + 0.5f) / outputSize - 0.5f
            val normalizedY = (y + 0.5f) / outputSize - 0.5f
            val screenX = centerX + normalizedX * cropDiameter
            val screenY = centerY + normalizedY * cropDiameter
            val dx = screenX - centerX
            val dy = screenY - centerY
            if ((dx * dx) + (dy * dy) > radius * radius) {
                pixels[y * outputSize + x] = 0
                continue
            }
            val bitmapX = ((screenX - imageLeft) / displayWidth * bitmap.width).roundToInt()
                .coerceIn(0, bitmap.width - 1)
            val bitmapY = ((screenY - imageTop) / displayHeight * bitmap.height).roundToInt()
                .coerceIn(0, bitmap.height - 1)
            pixels[y * outputSize + x] = bitmap.getPixel(bitmapX, bitmapY)
        }
    }
    output.setPixels(pixels, 0, outputSize, 0, 0, outputSize, outputSize)
    return ByteArrayOutputStream().use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        output.recycle()
        stream.toByteArray()
    }
}
