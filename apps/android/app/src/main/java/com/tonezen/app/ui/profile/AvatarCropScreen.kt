package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.avatar.AvatarCropTransform
import com.tonezen.app.domain.avatar.clampAvatarCropTransform
import com.tonezen.app.domain.avatar.cropAvatarToJpeg
import com.tonezen.app.domain.avatar.minAvatarCoverScale
import com.tonezen.app.ui.components.TonezenBackChromeBar
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun AvatarCropScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    imageUri: Uri,
    uploading: Boolean,
    onBack: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    val context = LocalContext.current
    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(imageUri) { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
        loadError = sourceBitmap == null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenSurface)
            .padding(padding),
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
                        stringResource(R.string.settings_account_avatar_load_error),
                        color = TonezenMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> {
                AvatarCropContent(
                    bitmap = sourceBitmap!!,
                    uploading = uploading,
                    onConfirm = onConfirm,
                )
            }
        }
        TonezenBackChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
            onBack = onBack,
            title = {
                Text(
                    stringResource(R.string.settings_account_avatar_crop_title),
                    color = TonezenInk,
                    fontWeight = FontWeight.SemiBold,
                )
            },
        )
    }
}

@Composable
private fun AvatarCropContent(
    bitmap: Bitmap,
    uploading: Boolean,
    onConfirm: (ByteArray) -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropTransform by remember(bitmap) { mutableStateOf(AvatarCropTransform()) }
    var minScale by remember(bitmap) { mutableFloatStateOf(1f) }
    val cropDiameterPx = remember(containerSize) {
        if (containerSize.width == 0 || containerSize.height == 0) {
            0f
        } else {
            minOf(containerSize.width, containerSize.height) * 0.72f
        }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 88.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.settings_account_avatar_crop_hint),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
                .pointerInput(bitmap, cropDiameterPx, minScale, containerSize, cropTransform) {
                    if (cropDiameterPx <= 0f) return@pointerInput
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = clampAvatarCropTransform(
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height,
                            containerWidth = containerSize.width.toFloat(),
                            containerHeight = containerSize.height.toFloat(),
                            cropDiameter = cropDiameterPx,
                            transform = AvatarCropTransform(
                                scale = cropTransform.scale * zoom,
                                offsetX = cropTransform.offsetX + pan.x,
                                offsetY = cropTransform.offsetY + pan.y,
                            ),
                            minScale = minScale,
                            maxScale = minScale * 4f,
                        )
                        cropTransform = next
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
            modifier = Modifier.fillMaxWidth(),
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
            Text(stringResource(R.string.settings_account_avatar_save))
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
        val fitScale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val displayWidth = bitmap.width * fitScale * transform.scale
        val displayHeight = bitmap.height * fitScale * transform.scale
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
        drawPath(dimPath, Color.Black.copy(alpha = 0.58f))
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
