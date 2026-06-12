package com.tonezen.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.DownloadGlyph
import com.tonezen.app.ui.components.SyncGlyph
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AuthStarField(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = TonezenTeal.copy(alpha = 0.05f),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.86f, size.height * 0.08f),
        )
        drawCircle(
            color = TonezenAmber.copy(alpha = 0.06f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.10f, size.height * 0.35f),
        )
        listOf(
            Offset(0.18f, 0.10f) to 0.004f,
            Offset(0.84f, 0.17f) to 0.003f,
            Offset(0.68f, 0.31f) to 0.0027f,
            Offset(0.24f, 0.48f) to 0.0028f,
            Offset(0.78f, 0.59f) to 0.0032f,
            Offset(0.46f, 0.72f) to 0.0025f,
        ).forEach { (point, radius) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = size.minDimension * radius,
                center = Offset(size.width * point.x, size.height * point.y),
            )
        }
    }
}

@Composable
internal fun AuthIntroPanel() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            color = TonezenInk,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.auth_headline),
                color = TonezenInk,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.auth_body),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthPill(
                label = stringResource(R.string.auth_offline_badge),
                tone = TonezenTeal,
                icon = { DownloadGlyph(tint = TonezenTeal, size = 12.dp) },
            )
            AuthPill(
                label = stringResource(R.string.auth_sync_badge),
                tone = TonezenAmber,
                icon = { SyncGlyph(tint = TonezenAmber, size = 12.dp) },
            )
        }
        AuthMediaStack()
    }
}

@Composable
private fun AuthPill(
    label: String,
    tone: Color,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, tone.copy(alpha = 0.24f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AuthMediaStack() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(top = 2.dp),
    ) {
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_midnight),
            brush = Brush.verticalGradient(listOf(Color(0xFF06111D), Color(0xFF12314C), Color(0xFF06111D))),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = 4.dp)
                .size(width = 118.dp, height = 158.dp)
                .graphicsLayer(rotationZ = -7f),
        )
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_atomic),
            brush = Brush.verticalGradient(listOf(Color(0xFFF5E8CE), Color(0xFFC9AA78))),
            contentColor = Color(0xFF6D4C2F),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-4).dp)
                .size(width = 142.dp, height = 178.dp)
                .graphicsLayer(rotationZ = 1.5f),
        )
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_body),
            brush = Brush.verticalGradient(listOf(Color(0xFF8D2E1B), Color(0xFFD65B28))),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-18).dp, y = 6.dp)
                .size(width = 112.dp, height = 150.dp)
                .graphicsLayer(rotationZ = 7f),
        )
    }
}

@Composable
private fun AuthMiniCover(
    title: String,
    brush: Brush,
    contentColor: Color = TonezenInk,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(12.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
        )
    }
}
