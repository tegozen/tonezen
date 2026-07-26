package com.tonezen.app.ui.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.BackNavButton
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
