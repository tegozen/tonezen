package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.avatar.AvatarCropTransform
import com.tonezen.app.domain.avatar.avatarCoverScale
import com.tonezen.app.domain.avatar.avatarCropDiameterPx
import com.tonezen.app.domain.avatar.clampAvatarCropTransform
import com.tonezen.app.ui.profile.cropAvatarToJpeg
import com.tonezen.app.domain.avatar.minAvatarCoverScale
import com.tonezen.app.ui.components.BackNavButton
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val AvatarCropBackground = Color(0xFF020617)

@Composable
internal fun AvatarCropScreen(
    imageUri: Uri,
    uploading: Boolean,
    uploadError: String? = null,
    onBack: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            loadOrientedAvatarBitmap(context, imageUri)
        }
        loadError = sourceBitmap == null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AvatarCropBackground),
    ) {
        when {
            sourceBitmap == null && !loadError -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TonezenTeal)
                }
            }
            loadError -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Не удалось открыть изображение",
                        color = TonezenMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                AvatarCropContent(
                    bitmap = sourceBitmap!!,
                    uploading = uploading,
                    uploadError = uploadError,
                    onConfirm = onConfirm,
                )
            }
        }
        AvatarCropTopBar(onBack = onBack)
    }
}

@Composable
private fun AvatarCropTopBar(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackNavButton(onClick = onBack)
            Text(
                "Фото профиля",
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 48.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Масштабируйте и сдвиньте фото, чтобы лицо было в круге",
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun AvatarCropContent(
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

@Composable
private fun AvatarCropCanvas(
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
