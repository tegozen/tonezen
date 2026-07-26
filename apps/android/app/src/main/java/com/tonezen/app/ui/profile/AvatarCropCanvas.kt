package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.avatar.AvatarCropTransform
import com.tonezen.app.domain.avatar.avatarCoverScale
import kotlin.math.roundToInt

@Composable
internal fun AvatarCropCanvas(
    bitmap: Bitmap,
    containerWidth: Float,
    containerHeight: Float,
    transform: AvatarCropTransform,
    cropDiameterPx: Float,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val coverScale = avatarCoverScale(bitmap.width, bitmap.height, size.width, size.height)
        val displayWidth = bitmap.width * coverScale * transform.scale
        val displayHeight = bitmap.height * coverScale * transform.scale
        val left = (size.width - displayWidth) / 2f + transform.offsetX
        val top = (size.height - displayHeight) / 2f + transform.offsetY
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt()),
        )
        val radius = cropDiameterPx / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val dimPath = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(
                Rect(
                    center.x - radius,
                    center.y - radius,
                    center.x + radius,
                    center.y + radius,
                ),
            )
            fillType = PathFillType.EvenOdd
        }
        drawPath(dimPath, Color.Black.copy(alpha = 0.68f))
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}
