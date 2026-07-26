package com.tonezen.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.coverBrush

@Composable
internal fun CycleCover(cycle: Cycle, modifier: Modifier = Modifier) {
    val brushSeed = remember(cycle.id) {
        Book(
            id = cycle.id,
            slug = cycle.slug,
            contentType = ContentType.AUDIOBOOK,
            title = cycle.title,
            author = cycle.books.firstOrNull()?.author,
        )
    }
    val brush = remember(cycle.id) { coverBrush(brushSeed) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.13f)), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.32f,
                center = Offset(size.width * 0.82f, size.height * 0.18f),
            )
            drawCircle(
                color = TonezenTeal.copy(alpha = 0.08f),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.18f, size.height * 0.88f),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = cycle.title.uppercase(),
                color = Color(0xFFFFE7BA),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${cycle.books.size} книг",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
