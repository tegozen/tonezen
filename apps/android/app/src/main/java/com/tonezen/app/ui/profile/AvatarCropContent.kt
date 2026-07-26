package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.avatar.AvatarCropTransform
import com.tonezen.app.domain.avatar.avatarCropDiameterPx
import com.tonezen.app.domain.avatar.clampAvatarCropTransform
import com.tonezen.app.domain.avatar.minAvatarCoverScale
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AvatarCropContent(
    bitmap: Bitmap,
    uploading: Boolean,
    uploadError: String?,
    onConfirm: (ByteArray) -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropTransform by remember(bitmap) { mutableStateOf(AvatarCropTransform()) }
    var minScale by remember(bitmap) { mutableFloatStateOf(1f) }
    val latestTransform = rememberUpdatedState(cropTransform)
    val cropDiameterPx = remember(containerSize) {
        avatarCropDiameterPx(
            containerWidth = containerSize.width.toFloat(),
            containerHeight = containerSize.height.toFloat(),
        )
    }

    LaunchedEffect(bitmap, containerSize, cropDiameterPx) {
        if (containerSize.width == 0 || cropDiameterPx <= 0f) return@LaunchedEffect
        val coverScale = minAvatarCoverScale(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            containerWidth = containerSize.width.toFloat(),
            containerHeight = containerSize.height.toFloat(),
            cropDiameter = cropDiameterPx,
        )
        minScale = coverScale
        cropTransform = AvatarCropTransform(scale = coverScale)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 96.dp)
                .onSizeChanged { containerSize = it }
                .pointerInput(bitmap, minScale, cropDiameterPx, containerSize.width, containerSize.height) {
                    if (cropDiameterPx <= 0f) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        val current = latestTransform.value
                        cropTransform = clampAvatarCropTransform(
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                            containerWidth = containerSize.width.toFloat(),
                            containerHeight = containerSize.height.toFloat(),
                            cropDiameter = cropDiameterPx,
                            transform = AvatarCropTransform(
                                scale = current.scale * zoom,
                                offsetX = current.offsetX + pan.x,
                                offsetY = current.offsetY + pan.y,
                            ),
                            minScale = minScale,
                            maxScale = minScale * 4f,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (containerSize.width > 0 && cropDiameterPx > 0f) {
                AvatarCropCanvas(
                    bitmap = bitmap,
                    containerWidth = containerSize.width.toFloat(),
                    containerHeight = containerSize.height.toFloat(),
                    transform = cropTransform,
                    cropDiameterPx = cropDiameterPx,
                )
            }
        }
        uploadError?.let { message ->
            Text(
                message,
                color = TonezenError,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Button(
            onClick = {
                if (containerSize.width == 0 || cropDiameterPx <= 0f || uploading) return@Button
                val jpegBytes = cropAvatarToJpeg(
                    bitmap = bitmap,
                    containerWidth = containerSize.width.toFloat(),
                    containerHeight = containerSize.height.toFloat(),
                    cropDiameter = cropDiameterPx,
                    transform = cropTransform,
                )
                onConfirm(jpegBytes)
            },
            enabled = !uploading && containerSize.width > 0,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
        ) {
            if (uploading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = TonezenAppBg,
                    strokeWidth = 2.dp,
                )
            }
            Text("Сохранить фото")
        }
    }
}
