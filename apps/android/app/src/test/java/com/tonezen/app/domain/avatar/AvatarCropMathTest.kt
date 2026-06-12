package com.tonezen.app.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AvatarCropMathTest {
    @Test
    fun minAvatarCoverScale_ensuresCropCircleFitsInsideDisplayedImage() {
        val scale = minAvatarCoverScale(
            bitmapWidth = 800,
            bitmapHeight = 600,
            containerWidth = 400f,
            containerHeight = 500f,
            cropDiameter = 200f,
        )
        val fitScale = minOf(400f / 800f, 500f / 600f)
        val fitWidth = 800 * fitScale * scale
        val fitHeight = 600 * fitScale * scale
        assertTrue(fitWidth >= 200f)
        assertTrue(fitHeight >= 200f)
    }

    @Test
    fun clampAvatarCropTransform_limitsPanWithinImageBounds() {
        val clamped = clampAvatarCropTransform(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            containerWidth = 400f,
            containerHeight = 400f,
            cropDiameter = 240f,
            transform = AvatarCropTransform(scale = 2f, offsetX = 500f, offsetY = -500f),
            minScale = 1.2f,
            maxScale = 4f,
        )
        assertEquals(2f, clamped.scale, 0.001f)
        val fitScale = minOf(400f / 1000f, 400f / 1000f)
        val displayWidth = 1000 * fitScale * clamped.scale
        val displayHeight = 1000 * fitScale * clamped.scale
        val maxOffsetX = kotlin.math.max(0f, (displayWidth - 240f) / 2f)
        val maxOffsetY = kotlin.math.max(0f, (displayHeight - 240f) / 2f)
        assertTrue(abs(clamped.offsetX) <= maxOffsetX + 0.01f)
        assertTrue(abs(clamped.offsetY) <= maxOffsetY + 0.01f)
    }

    @Test
    fun clampAvatarCropTransform_coercesScaleToRange() {
        val clamped = clampAvatarCropTransform(
            bitmapWidth = 500,
            bitmapHeight = 500,
            containerWidth = 300f,
            containerHeight = 300f,
            cropDiameter = 180f,
            transform = AvatarCropTransform(scale = 10f),
            minScale = 1.5f,
            maxScale = 3f,
        )
        assertEquals(3f, clamped.scale, 0.001f)
    }
}
